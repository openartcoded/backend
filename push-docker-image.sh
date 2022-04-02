set -e
docker build -t nbittich/api-backend .
docker tag nbittich/api-backend nbittich/api-backend
docker push nbittich/api-backend
