#Amazon EC2 and AWS Basics

##Before You Begin.
###EC2 Classic vs VPC (Virtual Private Cloud)
For the most part you won't have to worry about this as a new user, originally, EC2 only allowed a single IP space, that was shared among all EC2 users.

Later Amazon introduced VPCs, which allow you to have a virtually segregated network, where you can have both internal and external IP scopes assigned to your VPC, along with multiple VPCs. This allows you to segrate, let's say production from QA in the cloud.

Another benifit, form a security perspective is Layer 1 through Layer 3 can be certified compliant by Amazon, so you only need to worry about IP and up security.

###AWS Comand Line Interface install.
We will assume you have python installed, so do the following:

``` pip install awscli --upgrade ```

Let's make sure it works:

``` aws --version ```

We should get something similar to the following:

``` aws-cli/1.10.65 Python/2.7.11 Linux/4.6.6-200.fc23.x86_64 botocore/1.4.55 ```

###Configure Your Default Credentials.

``` aws configure ```

Example output below:

``` 
AWS Access Key ID [None]: AKIAJQARAIGdg3FGAFaEXTTQAWS Secret Access Key [None]: ASDGo---------------------------aF3raDefault region name [None]: us-east-1Default output format [None]: jsonwil@blackmirror:~$ ls .awsconfig	credentialswil@blackmirror:~$ cat .aws/config [default]output = jsonregion = us-east-1wil@blackmirror:~$ cat .aws/credentials [default]aws_access_key_id = AKIAJQARAIGdg3FGAFaEXTTQaws_secret_access_key = ASDGo---------------------------aF3ra```

###Amazon Regions and Availability Zones
These are broken into multiple geographic regions, each region has multiple zones. When you launch an instance into a zone, it can only be seen in that zone. ***Be aware of this as you may not notice old Instances and Resources that may be sitting idle and costing you money!***

For faster access between instances, keep the instances with the zone, for HA disperse resources through the zones.

To see your available regions, and zones do the following commands:

``` aws ec2 describe-regions --output table ```

And we see the following:

```
wbirkmaier@slim:~$ aws ec2 describe-regions --output table
----------------------------------------------------------
|                     DescribeRegions                    |
+--------------------------------------------------------+
||                        Regions                       ||
|+-----------------------------------+------------------+|
||             Endpoint              |   RegionName     ||
|+-----------------------------------+------------------+|
||  ec2.ap-south-1.amazonaws.com     |  ap-south-1      ||
||  ec2.eu-west-1.amazonaws.com      |  eu-west-1       ||
||  ec2.ap-southeast-1.amazonaws.com |  ap-southeast-1  ||
||  ec2.ap-southeast-2.amazonaws.com |  ap-southeast-2  ||
||  ec2.eu-central-1.amazonaws.com   |  eu-central-1    ||
||  ec2.ap-northeast-2.amazonaws.com |  ap-northeast-2  ||
||  ec2.ap-northeast-1.amazonaws.com |  ap-northeast-1  ||
||  ec2.us-east-1.amazonaws.com      |  us-east-1       ||
||  ec2.sa-east-1.amazonaws.com      |  sa-east-1       ||
||  ec2.us-west-1.amazonaws.com      |  us-west-1       ||
||  ec2.us-west-2.amazonaws.com      |  us-west-2       ||
|+-----------------------------------+------------------+|
wbirkmaier@slim:~$
```

###Get Help.

```
aws ec2 help
```

###Create A Public Private Key Pair.

``` aws ec2 create-key-pair --key-name mykeypairname --query 'KeyMaterial' --output text > mykeypair.pem ```

You must now chmod the keypair, in order to be able to use it:

```chmod 400 mykeypair.pem ```

**NOTE:** If you lose this private/public key pair, you will lose all access to your instance created with it, and will have no way to get on the system.

##What is an AMI?
An Amazon Machine Image is a XEN base image, that is pre-configured to work in Amazon's Cloud.

There are two types, Instance Store backed and Elastic Block Storage backed:

* **Instance Store Backed**
	* These AMIs, use ephemeral storage, once the instance is terminated the data is gone (can not be in a stopped state).
	* Requires extra work to make it into an AMI.
	* Kernel, Disk, etc are fixed for the life of the instance.
	* Require longer boot time, 5 min.

* **EBS Backed**
	* These use Amazon's block storage and are spun up from a cloned copy of an EBS snapshot 
	* Can be made into an AMI with one command
	* Can be shutdown (not terminated) and data will persist on the root volume.
	* Kernel, RAM, Volumes, etc can be changed when instance is stopped.
	* Less than 1 min boot time.

Either type of instance will lose it's root volume on termination, and EBS volumes will be kept.

My understanding is a Instance Store AMI boots a kernel image, then a ramdisk image, then mounts the root volume for boot.

A EBS Backed image, uses a snapped EBS volume, then boots the kernel image from the EBS volume, and mounts the file systems in the EBS volume.

Currently I see no reason not to use EBS based instances.

To see all available AMIs type the following:

``` aws ec2 describe-images --output table ```

**NOTE:** This will take several minutes and give you a huge list of AMIs.

If you would like a shorter list, and wish to sort on key words, try this(All amazon owned images and EBS volumes that are Paravirtual):

```
aws ec2 describe-images --owners amazon --query 'Images[*].[ImageId,ImageLocation,RootDeviceType,VirtualizationType]' --filters "Name=virtualization-type,Values=paravirtual" "Name=root-device-type,Values=ebs*"  --output json
```

##EC2 Instance Types.
There are many flavours of the type of instance to boot your AMI into. For an example some of options available are similar to what you will see below, and they all have different cost tiers, performance benefits, etc.  While 32bit is supported, 64bit is more than likely what you will want and storage is flexible:

* Small 1 vCPU 2GB 
* Large 4 vCPUs 8GB 
* Extra Large 8 vCPUs 16GB 
* High CPU Medium 5 vCPUs 2GB 
* High CPU Extra Large 20 vCPUs 8GB 

To launch an instance, type the following:

``` aws ec2 run-instances --image-id ami-008db468 --count 1 --instance-type t1.micro --key-name mykeypairname ```

***NOTE:*** the `mykeypairname` is the name of the keypair, in AWS, not the file name.  Also because we have not created a security group, the instance will launch with the default group and may not have appropriate ports open for us to get in.

We can see the instance status this way:

```aws ec2 describe-instance-status --instance-ids <instanceID>```

We can also terminate (Delete) the instance as follows:

```aws ec2 terminate-instances --instance-ids <instanceID>```

##IP Assignment and Security Groups
When you spin up an instance, you will have both a public IP and a internal Private IP. 

You will also have an instance associated with a security group, where you can specify which ports are open, and what the source IPs to those ports may be.  This is outside the instance firewall rules.

You can see your security groups with the following command:

```aws ec2 describe-security-groups```

Let's create a new security group and open ports that allow the whole internet:

```
wil@blackmirror:~$ aws ec2 create-security-group --group-name linuxSSH --description "SSH Port 22 for Linux"
{    "GroupId": "sg-2aeb8350"}	wil@blackmirror:~$ aws ec2 authorize-security-group-ingress --group-name linuxSSH --protocol tcp --port 22 --cidr 0.0.0.0/0wil@blackmirror:~$ 
```

Now we can launch an instance again, but with the above security group:

```aws ec2 run-instances --image-id ami-008db468 --security-group-ids sg-2aeb8350 --count 1 --instance-type t1.micro --key-name mykeypairname```

Keep in mind when you terminate the instance, the public IP is instantly freed, allowing another AWS user to allocated that IP for their own use. This is where Elastic IPs come into play.

To allocate an elastic IP:

```
wbirkmaier@slim:~$ aws ec2 allocate-address
{
    "PublicIp": "52.0.6.238",
    "Domain": "vpc",
    "AllocationId": "eipalloc-95f9afaa"
}
wbirkmaier@slim:~$
```

Once you allocate an elastic IP, it is your to keep for as long as you pay for it.

Here is how to show your existing elastic IPs within your VPC:

``` aws ec2 describe-addresses```

And associate them to an instance:

```aws ec2 associate-address --instance-id i-0b263919b6498b123 --allocation-id eipalloc-95f9afaa```

You can of course dis-associate the EIP and delete it so you wont' be charged:

```aws ec2 release-address --allocation-id eipalloc-95f9afaa```

**NOTE:** If you allocate and EIP, even if you are not using it, Amazon will bill for it indefinatly untile you release it.

##EBS (Elastic Block Storage)
As mentioned earlier, this is block storage you can allocate to an existing instance, that is more permenant.

To see your block devices in your VPC, do the following:

``` aws ec2 describe-volumes ```
 
To Create a volume:
 
```aws ec2 create-volume --size 80 --region us-east-1 --availability-zone us-east-1a --volume-type gp2```

To attach a volume:

```aws ec2 attach-volume --volume-id <volumeID> --instance-id <instanceID>``` 
 
##Spining up an Instance with AWS Comand Line Interface
This is a cookbook on what has to be done to spin up a linux instance in AWS and succesfully connect:

* Create Key pair (Should already be done)

``` aws ec2 create-key-pair --key-name mykeypairname --query 'KeyMaterial' --output text > mykeypair.pem ```

* Create Security Group

``` aws ec2 create-security-group --group-name linuxSSH --description "SSH Port 22 for Linux" ```

* Open ports in the security group

```aws ec2 authorize-security-group-ingress --group-name linuxSSH --protocol tcp --port 22 --cidr 0.0.0.0/0 ```

* Instantiate instance with your key pair and security group

``` aws ec2 run-instances --image-id ami-008db468 --security-group-ids sg-2aeb8350 --count 1 --instance-type t1.micro --key-name mykeypairname ```

* Connect to your instance

``` ssh -i mykepair.pem ec2-user@<publicIP> ```

##Console

###There are two types of logins

Standard master account log in:

``` http://console.aws.amazon.com ```

Specific IAM User Login:

```https://<accountID>.signin.aws.amazon.com/console```

##AWS Java SDK
###Setting Up Eclipse

Go ahead and download eclipse neon:

```https://www.eclipse.org/downloads/```
###Installing the SDK
Once you install eclipse, you can get the amazon SDK installed.

To Install the Toolkit:

* Open Help → Install New Software….
* Enter https://aws.amazon.com/eclipse in the text box labeled “Work with” at the top of the dialog.
* Select “AWS Toolkit for Eclipse” from the list below.
* Click “Next.” Eclipse guides you through the remaining installation steps.

###Code Examples

You can now click on the AWS icon, click "new java aws project" and pick the sample code.

**NOTE:** The SDK will use your credentials in ~/.aws