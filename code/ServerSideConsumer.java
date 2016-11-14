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

		AWSCredentials credentials = null;
        try {
        //	credentials = new BasicAWSCredentials("AKIA...", "ZDl..."); 
        	credentials = new ProfileCredentialsProvider("default").getCredentials();
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
