package au.org.aodn.aws.wps.operation;

import au.org.aodn.aws.util.EmailService;
import au.org.aodn.aws.util.JobFileUtil;
import au.org.aodn.aws.wps.request.ExecuteRequestHelper;
import au.org.aodn.aws.wps.status.*;
import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.AWSBatchClientBuilder;
import com.amazonaws.services.batch.model.SubmitJobRequest;
import com.amazonaws.services.batch.model.SubmitJobResult;
import net.opengis.wps.v_1_0_0.Execute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static au.org.aodn.aws.wps.status.WpsConfig.*;

public class ExecuteOperation implements Operation {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteOperation.class);


    private final Execute executeRequest;

    public ExecuteOperation(Execute executeRequest) {
        this.executeRequest = executeRequest;
    }


    @Override
    public String execute() {

        //  Config items:
        //      queue names
        //      job name
        //      AWS region
        //      status filename
        //      status location
        String statusS3BucketName = WpsConfig.getProperty(STATUS_S3_BUCKET_CONFIG_KEY);
        String jobFileS3KeyPrefix = WpsConfig.getProperty(AWS_BATCH_JOB_S3_KEY_PREFIX);
        String statusFileName = WpsConfig.getProperty(STATUS_S3_FILENAME_CONFIG_KEY);
        String requestFileName = WpsConfig.getProperty(REQUEST_S3_FILENAME_CONFIG_KEY);
        String jobName = WpsConfig.getProperty(AWS_BATCH_JOB_NAME_CONFIG_KEY);
        String jobQueueName = WpsConfig.getProperty(AWS_BATCH_JOB_QUEUE_NAME_CONFIG_KEY);
        String awsRegion = WpsConfig.getProperty(AWS_REGION_CONFIG_KEY);


        LOGGER.info("statusS3BucketName: " + statusS3BucketName);
        LOGGER.info("statusFileName: " + statusFileName);
        LOGGER.info("jobName: " + jobName);
        LOGGER.info("jobQueueName: " + jobQueueName);
        LOGGER.info("awsRegion: " + awsRegion);

        ExecuteRequestHelper helper = new ExecuteRequestHelper(executeRequest);
        String email = helper.getEmail();

        String processIdentifier = executeRequest.getIdentifier().getValue();  // code spaces not supported for the moment
        JobMapper jobMapper = new JobMapper(processIdentifier);
        String jobDefinitionName = jobMapper.getJobDefinitionName();

        LOGGER.info("Execute operation requested. Identifier [" + processIdentifier + "], Email [" + email + "]");
        LOGGER.info("Submitting job request...");
        SubmitJobRequest submitJobRequest = new SubmitJobRequest();

        //  TODO: at this point we will need to invoke the correct AWS batch processing job for the function that is specified in the Execute Operation
        //  This will probably involve selecting the appropriate queue, jobName (mainly for display in AWS console) & job definition based on the processIdentifier.
        submitJobRequest.setJobQueue(jobQueueName);  //TODO: config/jobqueue selection
        submitJobRequest.setJobName(jobName);
        submitJobRequest.setJobDefinition(jobDefinitionName);  //TODO: either map to correct job def or set vcpus/memory required appropriately

        AWSBatchClientBuilder builder = AWSBatchClientBuilder.standard();
        builder.setRegion(awsRegion);

        AWSBatch client = builder.build();
        SubmitJobResult result = client.submitJob(submitJobRequest);

        String jobId = result.getJobId();

        LOGGER.info("Batch job submitted. JobID [" + jobId + "], Identifier [" + processIdentifier + "], Email [" + email + "]");
        LOGGER.info("Writing job request file to S3");
        S3JobFileManager s3JobFileManager = new S3JobFileManager(statusS3BucketName, jobFileS3KeyPrefix, jobId);
        try {
            s3JobFileManager.write(JobFileUtil.createXmlDocument(executeRequest), requestFileName, STATUS_FILE_MIME_TYPE);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        String statusDocument;
        ExecuteStatusBuilder statusBuilder = new ExecuteStatusBuilder(jobId, statusS3BucketName, statusFileName);

        try {
            statusDocument = statusBuilder.createResponseDocument(EnumStatus.ACCEPTED, executeRequest.getIdentifier().getValue(), null, null, null);
            s3JobFileManager.write(statusDocument, statusFileName, STATUS_FILE_MIME_TYPE);

            if (email != null) {
                EmailService emailService = new EmailService();
                emailService.sendRegisteredJobEmail(email, jobId);
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e.getMessage(), e);
            //  Form failed status document
            statusDocument = statusBuilder.createResponseDocument(EnumStatus.FAILED, executeRequest.getIdentifier().getValue(),"Failed to create status file : " + e.getMessage(), "StatusFileError", null);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            statusDocument = statusBuilder.createResponseDocument(EnumStatus.FAILED, executeRequest.getIdentifier().getValue(), e.getMessage(), "EmailError", null);
        }

        return statusDocument;
    }

}
