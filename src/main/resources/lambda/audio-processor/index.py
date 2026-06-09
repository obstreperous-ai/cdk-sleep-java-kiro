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
    request_id = getattr(context, "aws_request_id", "unknown")

    _log_structured("INFO", request_id, "RECEIVED", "Received event", event=event)

    bucket_name = event.get("bucket", {}).get("name")
    object_key = event.get("object", {}).get("key")

    if not bucket_name:
        _log_structured("ERROR", request_id, "VALIDATION_FAILED",
                        "Missing required field: bucket.name")
        raise ValueError("Missing required field: bucket.name")
    if not object_key:
        _log_structured("ERROR", request_id, "VALIDATION_FAILED",
                        "Missing required field: object.key")
        raise ValueError("Missing required field: object.key")

    # Validate file extension (defense-in-depth - Choice state also validates)
    valid_extensions = (".wav", ".mp3", ".ogg")
    if not object_key.lower().endswith(valid_extensions):
        extension = object_key.rsplit(".", 1)[-1] if "." in object_key else ""
        _log_structured("ERROR", request_id, "VALIDATION_FAILED",
                        "Unsupported audio format", extension=extension)
        raise ValueError(f"Unsupported audio format: {extension}")

    audio_id = object_key

    response = {
        "status": "VALIDATED",
        "audioId": audio_id,
        "processingTimestamp": datetime.now(timezone.utc).isoformat(),
        "bucketName": bucket_name,
        "objectKey": object_key,
    }

    _log_structured("INFO", request_id, "COMPLETED", "Processing complete",
                    audio_id=audio_id, bucket_name=bucket_name, object_key=object_key)
    return response
