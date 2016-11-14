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
