#AWS AutoScaling Basics

For this exercise we will create a SQS Queue, and a small java app to consume the queue messages in SQS. Also another app to push messages to the queue. (Will demonstrate with AWS CLI too)
We will then create a new AMI that has this consumer.jar (or consume.sh), along with Cloud Watch Alarms that will trigger the scaling event, if the message queue gets to high. 

***NOTE:*** In order to complete this, you should have created an ELB earlier and understand how this works, as well as how to create a custom AMI, along with having Eclipse installed, or be able to run java code from the CLI on your system or understand the aws cli.

###Create the SQS Queue

```
wbirkmaier@slim:~$ aws sqs create-queue --queue-name genesysQueue
{
    "QueueUrl": "https://queue.amazonaws.com/082235327862/genesysQueue"
}
wbirkmaier@slim:~$
```

###Test the queue and add messages to the queue with java

First we will put our credentials in here, along with the queue from above, that we wish to populate messages into.  We can create a new AWS project for SQS in Eclipse, then past this code into the project and run it.  After a few seconds, stop the code, as it will flood the queue quite quickly.


```
import java.text.SimpleDateFormat;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageRequest;

/* How fast to send a messge to the queue in Milliseconds with integer waitMs */
public class ClientSideProducer {
    static final int waitMs   = 1;
	
    public static void main(String[] args) throws Exception {
	
	/* replace with your access key and secret key */
        AmazonSQS sqs = new AmazonSQSClient( new BasicAWSCredentials( "AKI...", "ZDl...") );

        System.out.println("======================");
        System.out.println("Place Message in Queue");
        System.out.println("======================\n");

        try {
        	while (true) {
        		String msg = "Message AutoScaling Test at: " 
				+ (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format( new Date() ) );
        		System.out.println("Sending a message: " + msg + "\n");
			/* SQS queue URL */
			sqs.sendMessage( new SendMessageRequest( "https://queue.amazonaws.com/082235327862/genesysQueue", msg ) );
			Thread.sleep( waitMs );
        	}

        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it " +
                    "to Amazon SQS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered " +
                    "a serious internal problem while trying to communicate with SQS, such as not " +
                    "being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
}
```

We will see an output similar to below:

```
======================
Place Message in Queue
======================

Sending a message: Message AutoScaling Test at: 2016/11/09 11:51:27

Sending a message: Message AutoScaling Test at: 2016/11/09 11:51:28

Sending a message: Message AutoScaling Test at: 2016/11/09 11:51:28

Sending a message: Message AutoScaling Test at: 2016/11/09 11:51:28

Sending a message: Message AutoScaling Test at: 2016/11/09 11:51:28

```

We can see the number of messages:

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws/code$ aws sqs get-queue-attributes --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --attribute-names ApproximateNumberOfMessages
{
    "Attributes": {
        "ApproximateNumberOfMessages": "100"
    }
}
wbirkmaier@slim:~/opt/repos/gitrepo/aws/code$
```

We could also just do this from the CLI in a for loop:

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws/code$ aws sqs send-message --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --message-body "Sending a message: Message AutoScaling Test"
{
    "MD5OfMessageBody": "2fc70f550a63c9d88963d5f19094d684",
    "MessageId": "c9256f80-812e-4a90-9e83-fa71e62382e1"
}
wbirkmaier@slim:~/opt/repos/gitrepo/aws/code$
```

And now see the queue increase by 1:

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws/code$ aws sqs get-queue-attributes --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --attribute-names ApproximateNumberOfMessages
{
    "Attributes": {
        "ApproximateNumberOfMessages": "101"
    }
}
wbirkmaier@slim:~/opt/repos/gitrepo/aws/code$
```

###Compile the Q consumer.java

We will now compile the consumer.java app in Eclipse, after making sure our AWS Credentials are in the code.

***NOTE:*** this does not have to be done, we can skip below and use the aws cli in consume.sh to effect the same thing.  Otherwise if you do this, you should compile for Java 1.5, the target OS supports that and execute this with cron.

```
import java.util.List;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.auth.BasicAWSCredentials;

public class ServerSideConsumer {

	/* How long in Milliseconds to read the Queue */
	static final int CONSUMER_INTERVAL_ms = 1000;

	public static void main(String[] args) throws Exception {
		/* We could also do a ~.AWS/credentials read too */
		AWSCredentials credentials = null;
        try {
        	credentials = new BasicAWSCredentials("AKI...", "ZDl...");
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location ~/.aws/credentials), and is in valid format.",
                    e);
        }

        AmazonSQS sqs = new AmazonSQSClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        sqs.setRegion(usEast1);

        int consumer_interval_ms = args.length > 0 ? Integer.valueOf( args[0] ).intValue() : CONSUMER_INTERVAL_ms;

        System.out.println("====================");
        System.out.println("Server Side Consumer");
        System.out.println("====================\n");

        try {
        	while (true) {
        		// List queues
        		for (String queueUrl : sqs.listQueues().getQueueUrls()) {
        			System.out.println("\nQueueUrl: " + queueUrl);

        			// Receive messages
        			System.out.print("Checking msg: " );
        			Message msg = consumeMsg( sqs, queueUrl );
        			if ( msg != null ) {
        				System.out.println( msg.getBody() );
        				String messageReceiptHandle = msg.getReceiptHandle();
        				sqs.deleteMessage(new DeleteMessageRequest(queueUrl, messageReceiptHandle));
        			} else {
        				System.out.println("No message consumed from queue.");
        			}
        		}
        		Thread.sleep( consumer_interval_ms );
        		System.out.println();
        	}
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it " +
                    "to Amazon SQS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered " +
                    "a serious internal problem while trying to communicate with SQS, such as not " +
                    "being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }

    private static Message consumeMsg( AmazonSQS qsvc, String queueUrl ) {
    	// Receive messages
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl)
        	.withMaxNumberOfMessages( 1 );
        List<Message> messages = qsvc.receiveMessage(receiveMessageRequest).getMessages();
        return messages.isEmpty() ? null : messages.get( 0 );
    }

}
```

###Create a security group

This gives us a security group with port 80 and 22 open:

```
wil@host:~$ aws ec2 create-security-group --group-name Linux22and80 --description "SSH Port 22 and HTTP Port 80 for Linux"
{
    "GroupId": "sg-19b45f64"
}
wil@host:~$ aws ec2 authorize-security-group-ingress --group-name linux22and80 --protocol tcp --port 22 --cidr 0.0.0.0/0
wil@host:~$ aws ec2 authorize-security-group-ingress --group-name linux22and80 --protocol tcp --port 80 --cidr 0.0.0.0/0wil@host:~$
```

###Create New Gold Instance Base to pull from our SQS queue

Now we will launch a CentOS 6 PV instances, with the security group from above:

```aws ec2 run-instances --image-id ami-bc8131d4 --security-group-ids sg-19b45f64 --count 1 --instance-type t1.micro --key-name mykeypairname```

###And get our Public IP for our instance that was launched:

``` wil@host:~$ aws ec2 describe-instances --instance-id i-8e753617 --query 'Reservations[*].Instances[*].NetworkInterfaces[*].PrivateIpAddresses[*].Association.PublicIp' --output text```

```
54.209.242.126
wil@host:~$
```

###Now we will ssh to the instance, epel, pip and the awscli tools:

```
wbirkmaier@slim:~/opt/aws/keys$ ssh -i wil.birkmaier-aws.pem root@ 54.209.242.126
```
```
yum install https://dl.fedoraproject.org/pub/epel/epel-release-latest-6.noarch.rpm -y
yum install -y python-pip
pip install awscli
```

###Verify AWS works:

```
[root@ip-172-31-4-13 ~]# aws --help
usage: aws [options] <command> <subcommand> [<subcommand> ...] [parameters]
To see help text, you can run:

  aws help
  aws <command> help
  aws <command> <subcommand> help
aws: error: too few arguments
[root@ip-172-31-4-13 ~]#
```

###Configure your aws credentials:

```
[root@ip-172-31-4-13 ~]# aws configure
AWS Access Key ID [None]: AKI...
AWS Secret Access Key [None]: ZDl...
Default region name [None]: us-east-1
Default output format [None]: json
[root@ip-172-31-4-13 ~]#
```

###Verify we can still see the messages in the queue from our instance now:

```
[root@ip-172-31-4-13 ~]# aws sqs get-queue-attributes --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --attribute-names ApproximateNumberOfMessages
{
    "Attributes": {
        "ApproximateNumberOfMessages": "101"
    }
}
[root@ip-172-31-4-13 ~]#
```

###Now, we will pull a single message from the queue, and then display the queue messages left:

```
[root@ip-172-31-4-13 ~]# aws sqs receive-message --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --attribute-names All --message-attribute-names All --max-number-of-messages 1
{
    "Messages": [
        {
            "Body": "Message AutoScaling Test at: 2016/11/09 11:51:30",
            "Attributes": {
                "ApproximateFirstReceiveTimestamp": "1478818228178",
                "SenderId": "082235327862",
                "ApproximateReceiveCount": "1",
                "SentTimestamp": "1478710290182"
            },
            "ReceiptHandle": "AQEBf7poLyutQY7sWwo3MGWOlXAhF+WasPpVmEFgsG/Kt8sfb3CPzIn75hm1ZCD40OE8lUSeOfBh5g6JdBtbTz+yuN2eDtnDELrQFyc6WnYPcr8AD1rQ51GgglY9a5HyPwyPsYDEfMbfFuXoT71QDGK+wf8XVyNRjlBWUwO47jMIfmcID/ozLmPJMOp6RcOGkPxgTzguVo+vQ6C6/UhkC1nmWNSbipVTyQZWOAqy/ky33C8z1qQohhQ3YvW9IMaS1Lmz/ANAYMVi7pyrXHkqGIknwwI5+WTUviOkmsjvuOb67ItvIzQ/+7cIyUxn1qKl3XH32tjgnD0kXlr0ac92mh+xDYbw7VXNtF6QO7troPN7QSGf4KvRqPl/r6lAKhFa0zmm",
            "MD5OfBody": "ca4302f16bb5bf33a4d1054399d18214",
            "MessageId": "a5749728-56f4-448b-9760-93495f360e5a"
        }
    ]
}
[root@ip-172-31-4-13 ~]# aws sqs get-queue-attributes --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --attribute-names ApproximateNumberOfMessages
{
    "Attributes": {
        "ApproximateNumberOfMessages": "100"
    }
}
[root@ip-172-31-4-13 ~]#
```

###In order to delete the message, we must get the ReceiptHandle and delete it

```
[root@ip-172-31-4-13 ~]# aws sqs delete-message --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --receipt-handle $(aws sqs receive-message --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --query 'Messages[*].ReceiptHandle' --max-number-of-messages 1 --output text)
[root@ip-172-31-4-13 ~]# aws sqs get-queue-attributes --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --attribute-names ApproximateNumberOfMessages
{
    "Attributes": {
        "ApproximateNumberOfMessages": "99"
    }
}
[root@ip-172-31-4-13 ~]#
```

###Create queue consume.sh file and add to cron:

This will remove a message every minute

```
[root@ip-172-31-4-13 ~]# cat consume.sh
#!/bin/bash
aws sqs delete-message --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --receipt-handle $(aws sqs receive-message --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --query 'Messages[*].ReceiptHandle' --max-number-of-messages 1 --output text)
[root@ip-172-31-4-13 ~]#
```
```
[root@ip-172-31-4-13 ~]# crontab -e
crontab: installing new crontab
[root@ip-172-31-4-13 ~]# crontab -l
* * * * * /root/consume.sh
[root@ip-172-31-4-13 ~]# pwd
/root
[root@ip-172-31-4-13 ~]# chmod a+x consume.sh
[root@ip-172-31-4-13 ~]#
```

###Now we will create an image

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws$ aws ec2 create-image --instance-id i-06b6980b3a5a00e4b --name "My Consumer" --description "Consumer for SQS queue"
{
    "ImageId": "ami-5c705c4b"
}
```

This can take some time:

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws$ aws ec2 describe-images --image-id ami-5c705c4b | grep State
            "State": "available",
wbirkmaier@slim:~/opt/repos/gitrepo/aws$
```

###Create 2 new instances and place in an ELB

***NOTE:*** The ELB  is not needed, but we will use it to demonstrate how to add scaled instances to it.

After a several minutes to create the AMI, we can now launch 2 more instances, based on our new Gold AMI:

```
wil@host:~$ aws ec2 run-instances --image-id ami-5c705c4b --security-group-ids sg-9461c1e9 --count 2 --instance-type t1.micro --key-name wil.birkmaier-key 
```
Once we have our 3 instance-ids, we can create a ELB, and add the instances to that ELB.

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws$ aws ec2 describe-instances --query 'Reservations[*].Instances[*].InstanceId' --output text
i-06b6980b3a5a00e4b
i-02cb45c996426c97d
i-0e79e976440c3c017
wbirkmaier@slim:~/opt/repos/gitrepo/aws$
```
***NOTE:*** If you have more than 3 instances, you will see many more listed above in your VPC, this is why it is important to create tags you can use for grouping, searching, etc.

This will create an ELB within our VPC, we need to know our security group from above, as well as the network in the VPC we want to create this in.  We can of course create a seperate security group for the ELB if we wish:

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws$ aws ec2 describe-instances --instance-id i-0e79e976440c3c017 --query 'Reservations[*].Instances[*].NetworkInterfaces[*].SubnetId'
[
    [
        [
            "subnet-50df0219"
        ]
    ]
]
wbirkmaier@slim:~/opt/repos/gitrepo/aws$
```

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws$ aws elb create-load-balancer --load-balancer-name genesysELB --listeners "Protocol=TCP,LoadBalancerPort=22,InstanceProtocol=TCP,InstancePort=22" --scheme internal --subnets subnet-50df0219 --security-groups sg-9461c1e9
{
    "DNSName": "internal-genesysELB-1910334657.us-east-1.elb.amazonaws.com"
}
wbirkmaier@slim:~/opt/repos/gitrepo/aws$
```

```
wil@host:~$ aws elb describe-load-balancers --query 'LoadBalancerDescriptions[*].LoadBalancerName'
[
    "my-app-load-balancer"
]
```

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws$ aws elb register-instances-with-load-balancer --load-balancer-name genesysELB --instances i-06b6980b3a5a00e4b i-02cb45c996426c97d i-0e79e976440c3c017
{
    "Instances": [
        {
            "InstanceId": "i-02cb45c996426c97d"
        },
        {
            "InstanceId": "i-0e79e976440c3c017"
        },
        {
            "InstanceId": "i-06b6980b3a5a00e4b"
        }
    ]
}
wbirkmaier@slim:~/opt/repos/gitrepo/aws$
```

We can see our queue has been quickly drained:

```
wbirkmaier@slim:~/opt/repos/gitrepo/aws$ aws sqs get-queue-attributes --queue-url https://queue.amazonaws.com/082235327862/genesysQueue --attribute-names ApproximateNumberOfMessages
{
    "Attributes": {
        "ApproximateNumberOfMessages": "3"
    }
}
wbirkmaier@slim:~/opt/repos/gitrepo/aws$
```

###Create Launch Configuration and Auto Scaling Group to know what AMIs to use and define the size of our group

We create the launch configuration:

```
wbirkmaier@slim:~$ aws autoscaling create-launch-configuration --launch-configuration-name genesysLaunchconfig --image-id ami-5c705c4b --instance-type t1.micro --key-name mykeypairname --security-groups "Linux22and80"
```
Now we create the autoscaling group:

```
wbirkmaier@slim:~$ aws autoscaling create-auto-scaling-group --auto-scaling-group-name genesys_autoscalegroup --launch-configuration-name genesysLaunchconfig --availability-zones us-east-1b us-east-1c --min-size 2 --max-size 10 --desired-capacity 4  --load-balancer-names genesysELB
```

###Create autoscaling policies for Scaling Up and Scaling Down instances

We need two policies, to allow scaling up and scaling down of our instances, by 1 in each direction.```wbirkmaier@slim:~$ aws autoscaling put-scaling-policy --auto-scaling-group-name genesys_autoscalegroup --policy-name genesysScale-up  --scaling-adjustment 1 --adjustment-type ChangeInCapacity --cooldown 50{    "PolicyARN": "arn:aws:autoscaling:us-east-1:082235327862:scalingPolicy:307641b8-235b-4276-b50a-60d4109293eb:autoScalingGroupName/genesys_autoscalegroup:policyName/genesysScale-up"}
```

```wbirkmaier@slim:~$ aws autoscaling put-scaling-policy --auto-scaling-group-name genesys_autoscalegroup --policy-name genesysScale-down  --scaling-adjustment -1 --adjustment-type ChangeInCapacity --cooldown 50{    "PolicyARN": "arn:aws:autoscaling:us-east-1:082235327862:scalingPolicy:328450d7-f80b-48f8-a5b7-71a5f33727f8:autoScalingGroupName/genesys_autoscalegroup:policyName/genesysScale-down"} ```
To check what our policies are:```wbirkmaier@slim:~$ aws autoscaling describe-policies```

###Create Cloud Watch Alarms

We can now set up our Cloud Watch alarms, by going here https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#metrics: and clicking on “SQS” under Metrics, then selecting “ApproximateNumberOfMessagesVisible” next to “genesysQueue”.Next, we will click “Create Alarm” in the bottom right of the screen, then in the “Name” field, enter genesysScaleUP and a “Description” of Scale Up. Change the “is:” to >= 500, then delete the notification action, but click “+AutoScaling Action” and select the genesys_autoscalegroup and the genesysScale-up – Add 1 instance, then click “Create Alarm”.Now, we will repeat again, click “Create Alarm” in the bottom right of the screen, then in the “Name” field, enter GenesysScaleDWN and a “Description” of Scale Down. Change the “is:” to <= 100, then delete the notification action, but click “+AutoScaling Action” and select the genesys_autoscalegroup and the genesysScale-down – Remove 1 instance, then click “Create Alarm”:###Test this all works

We can test everything works by either running the java app above, or doing a for loop on the cli and creating several hundred messags in the queue.  Try it and see what happens!

Check how many instances you have of what we created running now, it was 3, it should be 4.

Also, delete 2 instances, what happens?  You should see it go back to 4, as that is our new desired capicity with the autoscaling policy.