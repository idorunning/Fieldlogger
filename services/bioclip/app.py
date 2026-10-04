"""Optional, separately hosted BioCLIP 2 service. Not run by the web Worker."""
import io
import os
import secrets
from threading import Lock
from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from bioclip import Rank
from bioclip.predict import TreeOfLifeClassifier

app = FastAPI(docs_url=None, redoc_url=None)
model = None
lock = Lock()

@app.post('/identify')
def identify(image: UploadFile = File(...), authorization: str = Header(default='')):
    global model
    token = os.environ.get('BIOCLIP_TOKEN', '')
    if not token:
        raise HTTPException(503, 'Configure BIOCLIP_TOKEN before using the service')
    if not secrets.compare_digest(authorization, 'Bearer ' + token):
        raise HTTPException(401, 'Unauthorized')
    raw = image.file.read(4 * 1024 * 1024 + 1)
    if len(raw) > 4 * 1024 * 1024:
        raise HTTPException(413, 'Image too large')
    try:
        photo = Image.open(io.BytesIO(raw))
        if photo.width * photo.height > 20_000_000:
            raise HTTPException(413, 'Image dimensions too large')
        photo = photo.convert('RGB')
    except (UnidentifiedImageError, OSError):
        raise HTTPException(400, 'Invalid image')
    with lock:
        if model is None:
            model = TreeOfLifeClassifier()
        predictions = model.predict([photo], Rank.SPECIES)
    return {'predictions': [{'name': p['species'], 'score': float(p['score'])} for p in predictions[:5]]}
