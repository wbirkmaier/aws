#Amazon ELB: Elastic Load Balancer Basics

In this example, we will create 3 instances with ports 22 and 80, running Apache and PHP, behind a load balancer. We will also demonstrate that it works. 

***NOTE:*** What we won't cover is placing the instances in multiple Availabilty Zones, but that is not hard to do, once you understand how things work.  This work is also the basis for Amazon Autoscaling.

##Create a security group

This gives us a security group with port 80 and 22 open:

```
wil@host:~$ aws ec2 create-security-group --group-name Linux22and80 --description "SSH Port 22 and HTTP Port 80 for Linux"
{
    "GroupId": "sg-19b45f64"
}
wil@host:~$ aws ec2 authorize-security-group-ingress --group-name linux22and80 --protocol tcp --port 22 --cidr 0.0.0.0/0
wil@host:~$ aws ec2 authorize-security-group-ingress --group-name linux22and80 --protocol tcp --port 80 --cidr 0.0.0.0/0wil@host:~$ 
```

##Create Gold Image Instance of our "Application Server"

Now we will launch a CentOS 6 PV instances, with the security group from above:

```aws ec2 run-instances --image-id ami-bc8131d4 --security-group-ids sg-19b45f64 --count 1 --instance-type t1.micro --key-name mykeypairname```

And get our Public IP for our instance that was launched:

``` wil@host:~$ aws ec2 describe-instances --instance-id i-8e753617 --query 'Reservations[*].Instances[*].NetworkInterfaces[*].PrivateIpAddresses[*].Association.PublicIp' --output text```

```
52.90.134.139
wil@host:~$
```

Now we will ssh to the instance, install httpd and php, along with creating a simple php home page:

```
wbirkmaier@slim:~/opt/aws/keys$ ssh -i wil.birkmaier-aws.pem root@52.90.134.139
[root@ip-172-31-11-175 ~]# uname -a
Linux ip-172-31-11-175 2.6.32-431.29.2.el6.x86_64 #1 SMP Tue Sep 9 21:36:05 UTC 2014 x86_64 x86_64 x86_64 GNU/Linux
[root@ip-172-31-11-175 ~]# cat /etc/centos-release
CentOS release 6.5 (Final)
[root@ip-172-31-11-175 ~]#
```

```
[root@ip-172-31-11-175 ~]# yum -y install httpd php
```

```
root@ip-172-31-11-175 ~]# chkconfig httpd on; service httpd start
Starting httpd:                                            [  OK  ]
[root@ip-172-31-11-175 ~]# vi /var/www/html/index.php
```

index.php contents, this will give us the internal and external IP of the instance:

```
<?php echo $_SERVER['SERVER_ADDR']; $output = shell_exec('curl http://169.254.169.254/latest/meta-data/public-ipv4'); echo "<pre>$output</pre>"; ?>
```

We will disable the firewall:

```
[root@ip-172-31-11-175 ~]# service iptables stop
iptables: Setting chains to policy ACCEPT: filter          [  OK  ]
iptables: Flushing firewall rules:                         [  OK  ]
iptables: Unloading modules:                               [  OK  ]
[root@ip-172-31-11-175 ~]# chkconfig iptables off
[root@ip-172-31-11-175 ~]#
```

For good measure, we will update the image and reboot:

```
root@ip-172-31-11-175 ~]# yum -y update; reboot
```

***NOTE:*** After the system is back up, go to http://***publicIpAddress*** and you should see both your private and public EC2 instance IPs.

Now we will create our custom AMI, based on this instance:

```
wil@host:~$ aws ec2 create-image --instance-id i-8e753617 --name "My App Server" --description "My PHP App server for an ELB"
{
    "ImageId": "ami-6a0d537d"
}
wil@host:~$
```

After a several minutes to create the AMI, we can now launch 2 more instances, based on our new Gold AMI:

```
wil@host:~$ aws ec2 run-instances --image-id ami-6a0d537d --security-group-ids sg-19b45f64 --count 2 --instance-type t1.micro --key-name wil.birkmaier-key 
```

Once we have our 3 instance-ids, we can create a ELB, and add the instances to that ELB.

```
wil@host:~$ aws ec2 describe-instances --query 'Reservations[*].Instances[*].InstanceId' --output text
```
```
i-0ca7e495
i-0da7e494
i-8e753617
```

***NOTE:*** If you have more than 3 instances, you will see many more listed above in your VPC, this is why it is important to create tags you can use for grouping, searching, etc.

This will create an ELB within our VPC, we need to know our security group from above, as well as the network in the VPC we want to create this in.  We can of course create a seperate security group for the ELB if we wish:

```
wil@host:~$ aws ec2 describe-instances --instance-id i-8e753617 --query 'Reservations[*].Instances[*].NetworkInterfaces[*].SubnetId'
[
    [
        [
            "subnet-fe890788"
        ]
    ]
]
wil@host:~$
```

```
wil@host:~$ aws elb create-load-balancer --load-balancer-name my-app-load-balancer --listeners "Protocol=HTTP,LoadBalancerPort=80,InstanceProtocol=HTTP,InstancePort=80" --scheme internet-facing --subnets subnet-fe890788 --security-groups sg-19b45f64
{
    "DNSName": "my-app-load-balancer-1993936659.us-east-1.elb.amazonaws.com"
}
```

```
wil@host:~$ aws elb describe-load-balancers --query 'LoadBalancerDescriptions[*].LoadBalancerName'
[
    "my-app-load-balancer"
]
```

```
wil@host:~$ aws elb register-instances-with-load-balancer --load-balancer-name my-app-load-balancer --instances i-0ca7e495 i-0da7e494 i-8e753617
{
    "Instances": [
        {
            "InstanceId": "i-8e753617"
        },
        {
            "InstanceId": "i-0da7e494"
        },
        {
            "InstanceId": "i-0ca7e495"
        }
    ]
}
```

Finally, we need to enable a health check, so if a apache httpd daemon goes down, the instance is pulled out of the ELB:

```
wil@host:~$ aws elb configure-health-check --load-balancer-name my-app-load-balancer --health-check Target=HTTP:80/index.php,Interval=30,UnhealthyThreshold=2,HealthyThreshold=2,Timeout=3
```

Now, fire up your web browser and go to: http://my-app-load-balancer-1993936659.us-east-1.elb.amazonaws.com

***NOTE:*** Hitting Shift-F5 should show you each of the 3 IPs for your ELB.