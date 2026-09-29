from pydantic import BaseModel, Field


class BoundingBox(BaseModel):
    x: int
    y: int
    width: int
    height: int


class RecognitionResponse(BaseModel):
    plate_text: str = Field(alias="plateText")
    vehicle_type: str = Field(alias="vehicleType")
    detection_confidence: float = Field(alias="detectionConfidence")
    ocr_confidence: float = Field(alias="ocrConfidence")
    vehicle_confidence: float = Field(alias="vehicleConfidence")
    bounding_box: BoundingBox | None = Field(alias="boundingBox")
    frame_index: int = Field(alias="frameIndex")
    annotated_image_base64: str = Field(alias="annotatedImageBase64")
    message: str

    model_config = {"populate_by_name": True}
