#todo temp solution
set -e
docker build -t artcoded/api-backend .
docker tag artcoded/api-backend artcoded:5000/artcoded/api-backend
docker push artcoded:5000/artcoded/api-backend
