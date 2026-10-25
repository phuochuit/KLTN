from datetime import datetime, timezone
from uuid import uuid4

from pydantic import BaseModel, Field


class BoundingBox(BaseModel):
    x: int
    y: int
    width: int
    height: int


class RecognitionResponse(BaseModel):
    contract_version: str = Field(default="1.0", alias="contractVersion")
    request_id: str = Field(default_factory=lambda: str(uuid4()), alias="requestId")
    evidence_id: str = Field(default_factory=lambda: str(uuid4()), alias="evidenceId")
    processed_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), alias="processedAt")
    model_version: str = Field(default="unknown", alias="modelVersion")
    plate_text: str = Field(alias="plateText")
    vehicle_type: str = Field(alias="vehicleType")
    detection_confidence: float = Field(alias="detectionConfidence")
    ocr_confidence: float = Field(alias="ocrConfidence")
    vehicle_confidence: float = Field(alias="vehicleConfidence")
    bounding_box: BoundingBox | None = Field(alias="boundingBox")
    frame_index: int = Field(alias="frameIndex")
    annotated_image_base64: str = Field(alias="annotatedImageBase64")
    quality: dict | None = Field(default=None, alias="quality")
    message: str

    model_config = {"populate_by_name": True}
