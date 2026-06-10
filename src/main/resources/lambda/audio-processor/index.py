"""Sleep Audio Processor Lambda function.

Receives events from Step Functions containing S3 bucket, key, and audioId
from the previous PutItem state output. Downloads input from S3, processes
audio using Amazon Polly for text-to-speech synthesis, uploads the result
to the output S3 bucket, and updates DynamoDB with output metadata.
"""

import json
import logging
import os
import uuid
from datetime import datetime, timezone

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

TABLE_NAME = os.environ.get("TABLE_NAME", "")
INPUT_BUCKET_NAME = os.environ.get("INPUT_BUCKET_NAME", "")
OUTPUT_BUCKET_NAME = os.environ.get("OUTPUT_BUCKET_NAME", "")

s3_client = boto3.client("s3")
polly_client = boto3.client("polly")
dynamodb_client = boto3.client("dynamodb")


def _log_structured(level, request_id, status, message, **kwargs):
    """Emit a structured JSON log entry."""
    entry = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "request_id": request_id,
        "level": level,
        "status": status,
        "message": message,
    }
    entry.update(kwargs)
    if level == "ERROR":
        logger.error(json.dumps(entry))
    else:
        logger.info(json.dumps(entry))


def _get_text_for_synthesis(bucket_name, object_key, request_id):
    """Download input file and determine text for Polly synthesis.

    For .txt files, reads the content directly as text input.
    For audio files (.wav, .mp3, .ogg), generates a soothing narration
    based on the filename to create a sleep-focused audio companion.

    Args:
        bucket_name: S3 bucket containing the input file.
        object_key: S3 object key for the input file.
        request_id: Lambda request ID for logging.

    Returns:
        str: Text to be synthesized by Polly.
    """
    extension = object_key.rsplit(".", 1)[-1].lower() if "." in object_key else ""

    if extension == "txt":
        _log_structured("INFO", request_id, "DOWNLOADING",
                        "Downloading text file from S3",
                        bucket=bucket_name, key=object_key)
        response = s3_client.get_object(Bucket=bucket_name, Key=object_key)
        text_content = response["Body"].read().decode("utf-8").strip()

        # Polly sync API supports up to 3000 characters
        if len(text_content) > 3000:
            text_content = text_content[:3000]

        _log_structured("INFO", request_id, "TEXT_EXTRACTED",
                        "Text content extracted from file",
                        text_length=len(text_content))
        return text_content
    else:
        # For audio files, generate a relaxation narration based on the filename
        base_name = object_key.rsplit("/", 1)[-1].rsplit(".", 1)[0] if "/" in object_key else object_key.rsplit(".", 1)[0]
        narration = (
            f"Welcome to your sleep audio session. "
            f"This relaxation track, {base_name}, has been prepared for you. "
            f"Close your eyes, take a deep breath, and let yourself drift into a peaceful sleep. "
            f"Allow all tension to leave your body as you sink deeper into relaxation."
        )
        _log_structured("INFO", request_id, "NARRATION_GENERATED",
                        "Generated narration for audio file",
                        base_name=base_name)
        return narration


def _synthesize_and_upload(text, output_key, request_id):
    """Use Polly to synthesize text and upload the result to S3.

    Args:
        text: Text to synthesize.
        output_key: S3 key for the output file.
        request_id: Lambda request ID for logging.

    Returns:
        dict: Output metadata including file size.
    """
    _log_structured("INFO", request_id, "SYNTHESIZING",
                    "Calling Polly SynthesizeSpeech",
                    text_length=len(text), output_key=output_key)

    polly_response = polly_client.synthesize_speech(
        Text=text,
        OutputFormat="mp3",
        VoiceId="Joanna",
        Engine="neural"
    )

    audio_stream = polly_response["AudioStream"].read()
    file_size = len(audio_stream)

    _log_structured("INFO", request_id, "UPLOADING",
                    "Uploading synthesized audio to S3",
                    output_bucket=OUTPUT_BUCKET_NAME,
                    output_key=output_key,
                    file_size=file_size)

    s3_client.put_object(
        Bucket=OUTPUT_BUCKET_NAME,
        Key=output_key,
        Body=audio_stream,
        ContentType="audio/mpeg"
    )

    return {
        "fileSize": file_size,
        "format": "mp3",
        "contentType": "audio/mpeg"
    }


def _update_dynamodb(audio_id, output_key, output_uri, file_size, request_id):
    """Update DynamoDB metadata record with output information.

    Args:
        audio_id: The partition key (audioId) of the record.
        output_key: S3 key of the output file.
        output_uri: Full S3 URI of the output file.
        file_size: Size of the output file in bytes.
        request_id: Lambda request ID for logging.
    """
    _log_structured("INFO", request_id, "UPDATING_DB",
                    "Updating DynamoDB with output metadata",
                    audio_id=audio_id, output_uri=output_uri)

    dynamodb_client.update_item(
        TableName=TABLE_NAME,
        Key={"audioId": {"S": audio_id}},
        UpdateExpression="SET #ob = :outputBucket, #ok = :outputKey, #ou = :outputUri, #fs = :fileSize, #fmt = :format",
        ExpressionAttributeNames={
            "#ob": "outputBucket",
            "#ok": "outputKey",
            "#ou": "outputUri",
            "#fs": "fileSize",
            "#fmt": "outputFormat"
        },
        ExpressionAttributeValues={
            ":outputBucket": {"S": OUTPUT_BUCKET_NAME},
            ":outputKey": {"S": output_key},
            ":outputUri": {"S": output_uri},
            ":fileSize": {"N": str(file_size)},
            ":format": {"S": "mp3"}
        }
    )


def handler(event, context):
    """Process audio from Step Functions input.

    Downloads input from S3, synthesizes speech using Amazon Polly,
    uploads the processed audio to the output S3 bucket, and updates
    DynamoDB with output metadata.

    Args:
        event: Step Functions input containing bucket name, object key,
               and audioId from the PutItem task output.
        context: Lambda context object.

    Returns:
        dict with status, audioId, outputBucket, outputKey, outputUri,
        fileSize, and format on success.

    Raises:
        ValueError: If required fields (bucket.name or object.key) are missing.
            The unhandled exception triggers the Step Functions Catch block.
    """
    request_id = getattr(context, "aws_request_id", "unknown")

    bucket_name = event.get("bucket", {}).get("name")
    object_key = event.get("object", {}).get("key")

    _log_structured("INFO", request_id, "RECEIVED", "Received event",
                    bucket_name=bucket_name, object_key=object_key)

    if not bucket_name:
        _log_structured("ERROR", request_id, "VALIDATION_FAILED",
                        "Missing required field: bucket.name")
        raise ValueError("Missing required field: bucket.name")
    if not object_key:
        _log_structured("ERROR", request_id, "VALIDATION_FAILED",
                        "Missing required field: object.key")
        raise ValueError("Missing required field: object.key")

    # Validate file extension (defense-in-depth - Choice state also validates)
    valid_extensions = (".wav", ".mp3", ".ogg", ".txt")
    if not object_key.lower().endswith(valid_extensions):
        extension = object_key.rsplit(".", 1)[-1] if "." in object_key else ""
        _log_structured("ERROR", request_id, "VALIDATION_FAILED",
                        "Unsupported audio format", extension=extension)
        raise ValueError(f"Unsupported audio format: {extension}")

    audio_id = object_key

    # Generate unique output key
    unique_id = str(uuid.uuid4())[:8]
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    base_name = object_key.rsplit(".", 1)[0] if "." in object_key else object_key
    # Remove any path prefixes for the output key
    base_name = base_name.rsplit("/", 1)[-1] if "/" in base_name else base_name
    output_key = f"processed/{base_name}-{timestamp}-{unique_id}.mp3"

    try:
        # Step 1: Get text for synthesis
        text = _get_text_for_synthesis(bucket_name, object_key, request_id)

        # Step 2: Synthesize speech and upload to S3
        output_metadata = _synthesize_and_upload(text, output_key, request_id)

        # Step 3: Update DynamoDB
        output_uri = f"s3://{OUTPUT_BUCKET_NAME}/{output_key}"
        _update_dynamodb(audio_id, output_key, output_uri,
                         output_metadata["fileSize"], request_id)

    except ValueError:
        raise
    except Exception as e:
        _log_structured("ERROR", request_id, "PROCESSING_FAILED",
                        f"Audio processing failed: {str(e)}",
                        error_type=type(e).__name__)
        raise RuntimeError(f"Audio processing failed: {str(e)}") from e

    output_uri = f"s3://{OUTPUT_BUCKET_NAME}/{output_key}"
    response = {
        "status": "COMPLETED",
        "audioId": audio_id,
        "outputBucket": OUTPUT_BUCKET_NAME,
        "outputKey": output_key,
        "outputUri": output_uri,
        "fileSize": output_metadata["fileSize"],
        "format": output_metadata["format"],
        "processingTimestamp": datetime.now(timezone.utc).isoformat(),
    }

    _log_structured("INFO", request_id, "COMPLETED", "Processing complete",
                    audio_id=audio_id, output_uri=output_uri,
                    file_size=output_metadata["fileSize"])
    return response
