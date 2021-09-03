CONTAINER_NAME=hermes-db

db:	rm-db
	docker run --name ${CONTAINER_NAME} -p 27017:27017 -d mongo
stop-db:
	docker stop ${CONTAINER_NAME} | true
rm-db: stop-db
	docker rm ${CONTAINER_NAME} | true