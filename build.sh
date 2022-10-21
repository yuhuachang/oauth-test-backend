#!/bin/bash

rm -rf oauth-test-frontend
git clone https://github.com/yuhuachang/oauth-test-frontend.git
cd oauth-test-frontend
npm install
npm run build
cd ..
mv oauth-test-frontend/build src/main/resources/static
./gradlew clean build


