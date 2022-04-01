set -e # fail script on error

if [[ -z "$1" || -z "$2" ]]
then
  echo "version mut be provided"
  exit -1;
fi

releaseVersion=$1
nextVersion=$2-SNAPSHOT

echo "release" $releaseVersion ", next" $nextVersion

mvn --batch-mode -Dtag=$releaseVersion release:prepare \
                 -DreleaseVersion=$releaseVersion \
                 -DdevelopmentVersion=$nextVersion

mvn release:clean
git pull

git checkout $releaseVersion
docker build -t artcoded/api-backend:v$releaseVersion .
docker tag artcoded/api-backend:v$releaseVersion artcoded:5000/artcoded/api-backend:v$releaseVersion
docker push artcoded:5000/artcoded/api-backend:v$releaseVersion

git checkout main
