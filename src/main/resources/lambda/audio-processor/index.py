"""Sleep Audio Processor Lambda function.

Receives events from Step Functions containing S3 bucket, key, and audioId
from the previous PutItem state output. Validates required fields and returns
enriched metadata for downstream processing.
"""

import json
import logging
import os
from datetime import datetime, timezone

logger = logging.getLogger()
logger.setLevel(logging.INFO)

TABLE_NAME = os.environ.get("TABLE_NAME", "")


def handler(event, context):
    """Process audio metadata from Step Functions input.

    Args:
        event: Step Functions input containing bucket name, object key,
               and audioId from the PutItem task output.
        context: Lambda context object.

    Returns:
        dict with status, audioId, and processingTimestamp on success.

    Raises:
        ValueError: If required fields (bucket.name or object.key) are missing.
            The unhandled exception triggers the Step Functions Catch block.
    """
    logger.info("Received event: %s", json.dumps(event))

    bucket_name = event.get("bucket", {}).get("name")
    object_key = event.get("object", {}).get("key")

    if not bucket_name:
        logger.error("Processing failed: Missing required field: bucket.name")
        raise ValueError("Missing required field: bucket.name")
    if not object_key:
        logger.error("Processing failed: Missing required field: object.key")
        raise ValueError("Missing required field: object.key")

    audio_id = object_key

    response = {
        "status": "VALIDATED",
        "audioId": audio_id,
        "processingTimestamp": datetime.now(timezone.utc).isoformat(),
        "bucketName": bucket_name,
        "objectKey": object_key,
    }

    logger.info("Processing complete: %s", json.dumps(response))
    return response
