## running the real data stream

### initializing the nexmark-server from the nexmark-bench-nexmark-server docker container:
```
nexmark-bench/bin/nexmark-server -c
```

### creating the events:
```
nexmark-bench/bin/nexmark-server --event-rate 500000 --max-events 300000000 --num-event-generators 8
```

### fresh docker image build
```
docker-compose down -v
docker rmi flink-nexmark
docker system prune -f
docker build -t flink-nexmark .
```

## running the test-data-generator

### create kafka topic
```
docker-compose exec kafka kafka-topics --create \
    --topic nexmark-events \
    --bootstrap-server kafka:9092 \
    --partitions 1 \
    --replication-factor 1
```

### make sure the postgresql sink has been established
```
docker-compose exec postgres psql -U postgres -d nexmark -c "\dt nexmark.*"
```

### generate the test data
```
docker-compose exec -it jobmanager python test_data_generator.py --bootstrap-servers kafka:9092 \
    --num-persons 100 \
    --num-auctions 1000000 \
    --save-json
```

### viewing the test data
```
docker-compose exec kafka kafka-console-consumer \
    --bootstrap-server kafka:9092 \
    --topic nexmark-events \
    --from-beginning > datagen.log
```

### run the flink job
```
docker-compose exec jobmanager flink run -py /opt/flink/flink_job.py -d
```

### view the postgres output
```
docker-compose exec postgres psql -U postgres -d nexmark -c "SELECT * FROM nexmark.q3_results;"
```