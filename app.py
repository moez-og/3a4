import cv2
import numpy as np
import base64
from flask import Flask, request, jsonify
from flask_cors import CORS
import os
import tempfile

app = Flask(__name__)
CORS(app)

MODEL_NAME = 'Facenet512'
DISTANCE_METRIC = 'cosine'
MANUAL_MAX_DISTANCE = 0.30
MIN_FACE_AREA_RATIO = 0.015
API_VERSION = 'faceid-balanced-v5'
_DEEPFACE = None
_FACE_CASCADE = None


def get_deepface():
    global _DEEPFACE
    if _DEEPFACE is None:
        from deepface import DeepFace
        _DEEPFACE = DeepFace
    return _DEEPFACE


def get_face_cascade():
    global _FACE_CASCADE
    if _FACE_CASCADE is None:
        cascade_path = os.path.join(cv2.data.haarcascades, 'haarcascade_frontalface_default.xml')
        _FACE_CASCADE = cv2.CascadeClassifier(cascade_path)
    return _FACE_CASCADE


def load_image_from_base64(image_data):
    try:
        payload = image_data.split(',', 1)[1] if ',' in image_data else image_data
        nparr = np.frombuffer(base64.b64decode(payload), np.uint8)
        return cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    except Exception as e:
        print(f"Erreur lors du chargement de l'image : {e}")
        return None


def has_clear_face(image):
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    cascade = get_face_cascade()

    faces = cascade.detectMultiScale(
        gray,
        scaleFactor=1.1,
        minNeighbors=5,
        minSize=(64, 64)
    )

    if len(faces) == 0:
        return False, 0.0, 0

    image_area = float(image.shape[0] * image.shape[1])
    largest_area = max(float(w * h) for (_, _, w, h) in faces)
    face_area_ratio = largest_area / image_area if image_area > 0 else 0.0

    return face_area_ratio >= MIN_FACE_AREA_RATIO, face_area_ratio, len(faces)


def extract_embedding(deepface, image_path):
    representations = deepface.represent(
        img_path=image_path,
        model_name=MODEL_NAME,
        enforce_detection=True,
        detector_backend='opencv'
    )

    if not representations:
        raise ValueError("Aucun visage détecté.")

    def face_area(rep):
        area = rep.get('facial_area') or {}
        w = float(area.get('w', 0))
        h = float(area.get('h', 0))
        return w * h

    best_representation = max(representations, key=face_area)
    embedding = best_representation.get('embedding')
    if embedding is None:
        raise ValueError("Embedding introuvable.")

    vector = np.asarray(embedding, dtype=np.float32)
    if vector.size == 0:
        raise ValueError("Embedding vide.")

    return vector, len(representations)


def cosine_distance(vec1, vec2):
    norm1 = np.linalg.norm(vec1)
    norm2 = np.linalg.norm(vec2)
    if norm1 == 0.0 or norm2 == 0.0:
        return 1.0
    similarity = float(np.dot(vec1, vec2) / (norm1 * norm2))
    return 1.0 - similarity


def compare_faces(uploaded_image, reference_image_path):
    temp_uploaded_path = None
    try:
        has_face, face_area_ratio, face_count = has_clear_face(uploaded_image)
        if not has_face:
            return {
                'success': False,
                'message': 'Aucun visage clair détecté dans l\'image caméra.',
                'face_area_ratio': face_area_ratio,
                'face_count': face_count
            }

        deepface = get_deepface()

        if not os.path.exists(reference_image_path):
            return {'success': False, 'message': 'Image de référence introuvable.'}

        with tempfile.NamedTemporaryFile(delete=False, suffix='.jpg') as temp_file:
            temp_uploaded_path = temp_file.name
            cv2.imwrite(temp_uploaded_path, uploaded_image)

        emb_uploaded, uploaded_detected_faces = extract_embedding(deepface, temp_uploaded_path)
        emb_reference, reference_detected_faces = extract_embedding(deepface, reference_image_path)

        distance = cosine_distance(emb_uploaded, emb_reference)
        strict_threshold = MANUAL_MAX_DISTANCE
        strict_match = distance <= strict_threshold

        print(
            "DeepFace => "
            f"distance={distance:.6f}, "
            f"strict_threshold={strict_threshold:.6f}, strict_match={strict_match}, "
            f"face_area_ratio={face_area_ratio:.6f}, face_count={face_count}, "
            f"uploaded_detected_faces={uploaded_detected_faces}, reference_detected_faces={reference_detected_faces}"
        )

        if strict_match:
            return {
                'success': True,
                'message': 'Les visages correspondent.',
                'distance': distance,
                'threshold': None,
                'strict_threshold': strict_threshold,
                'face_area_ratio': face_area_ratio,
                'face_count': face_count,
                'uploaded_detected_faces': uploaded_detected_faces,
                'reference_detected_faces': reference_detected_faces
            }

        return {
            'success': False,
            'message': 'Les visages ne correspondent pas.',
            'distance': distance,
            'threshold': None,
            'strict_threshold': strict_threshold,
            'face_area_ratio': face_area_ratio,
            'face_count': face_count,
            'uploaded_detected_faces': uploaded_detected_faces,
            'reference_detected_faces': reference_detected_faces
        }

    except Exception as e:
        print(f'Erreur lors de la comparaison des visages : {e}')
        return {'success': False, 'message': f'Erreur lors de la comparaison des visages : {str(e)}'}
    finally:
        if temp_uploaded_path and os.path.exists(temp_uploaded_path):
            os.remove(temp_uploaded_path)

@app.route('/compare-face', methods=['POST'])
def compare_face_endpoint():
    data = request.get_json()

    image_data = data.get('image')
    reference_image_path = data.get('reference')

    if not image_data or not reference_image_path:
        return jsonify({'success': False, 'message': 'Image ou chemin de référence manquant.', 'api_version': API_VERSION})

    uploaded_image = load_image_from_base64(image_data)

    if uploaded_image is None:
        return jsonify({'success': False, 'message': 'Erreur lors du chargement de l\'image.', 'api_version': API_VERSION})

    comparison_result = compare_faces(uploaded_image, reference_image_path)
    comparison_result['api_version'] = API_VERSION
    return jsonify(comparison_result)

if __name__ == '__main__':
    app.run(port=5501, debug=False, use_reloader=False)