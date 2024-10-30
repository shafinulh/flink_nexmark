import json
import logging
import argparse
from datetime import datetime, timedelta
from kafka import KafkaProducer
from kafka.errors import KafkaError
from typing import Dict, List
from concurrent.futures import ThreadPoolExecutor
import itertools

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class NexmarkTestDataGenerator:
    def __init__(self, bootstrap_servers: str, topic: str):
        # Configure producer with batch settings for better throughput
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda x: json.dumps(x).encode('utf-8'),
            retries=5,
            acks='all',
            batch_size=16384,  # Increase batch size
            linger_ms=5,  # Small delay to allow batching
            compression_type='gzip'  # Add compression
        )
        self.topic = topic
        self.base_time = datetime.now()
        
        # Pre-compute static data
        self.states = ["OR", "ID", "CA"]
        self.cities = {
            "OR": ["Portland", "Salem", "Eugene"],
            "ID": ["Boise", "Nampa", "Meridian"],
            "CA": ["San Francisco", "Los Angeles", "San Diego"]
        }
        self.categories = [10, 20, 30]
        logger.info(f"Initialized producer for topic {topic} at {bootstrap_servers}")

    def generate_person(self, id: int) -> Dict:
        state = self.states[id % 3]
        return {
            "event_type": 0,
            "person": {
                "id": id,
                "name": f"Person_{id}",
                "emailAddress": f"person_{id}@test.com",
                "creditCard": f"CC_{id}",
                "city": self.cities[state][id % 3],
                "state": state,
                "dateTime": (self.base_time + timedelta(seconds=id)).isoformat(),
                "extra": "test_data"
            }
        }

    def generate_auction(self, id: int, seller_id: int) -> Dict:
        return {
            "event_type": 1,
            "auction": {
                "id": id,
                "itemName": f"Item_{id}",
                "description": f"Description_{id}",
                "initialBid": 100 * (id % 5 + 1),
                "reserve": 200 * (id % 5 + 1),
                "dateTime": (self.base_time + timedelta(seconds=id)).isoformat(),
                "expires": (self.base_time + timedelta(days=1)).isoformat(),
                "seller": seller_id,
                "category": self.categories[id % 3],
                "extra": "test_data"
            }
        }

    def generate_test_dataset(self, num_persons: int, num_auctions: int) -> List[Dict]:
        """Generate a deterministic test dataset using parallel processing"""
        logger.info(f"Generating {num_persons} person records and {num_auctions} auction records...")
        
        # Use ThreadPoolExecutor for parallel generation
        with ThreadPoolExecutor() as executor:
            # Generate persons
            persons = list(executor.map(self.generate_person, range(num_persons)))
            
            # Generate auctions with seller_ids
            auction_params = [(i, i % num_persons) for i in range(num_auctions)]
            auctions = list(executor.map(lambda p: self.generate_auction(*p), auction_params))
        
        # Combine and return all records
        return persons + auctions

    def send_test_data(self, test_data: List[Dict]):
        """Send test data to Kafka topic in batches"""
        logger.info(f"Starting to send {len(test_data)} events to topic {self.topic}")
        
        BATCH_SIZE = 1000  # Adjust based on your memory constraints
        success_count = 0
        error_count = 0
        
        # Process in batches
        for i in range(0, len(test_data), BATCH_SIZE):
            batch = test_data[i:i + BATCH_SIZE]
            futures = []
            
            # Send batch asynchronously
            for event in batch:
                future = self.producer.send(self.topic, event)
                futures.append(future)
            
            # Wait for batch completion
            for future in futures:
                try:
                    future.get(timeout=10)
                    success_count += 1
                except KafkaError as e:
                    logger.error(f"Error sending event: {e}")
                    error_count += 1
                
        self.producer.flush()
        logger.info(f"Completed sending events. Success: {success_count}, Errors: {error_count}")

def main():
    parser = argparse.ArgumentParser(description='Generate and send test data to Kafka topic')
    parser.add_argument('--bootstrap-servers', default='kafka1:9092',
                      help='Kafka bootstrap servers')
    parser.add_argument('--topic', default='nexmark-events',
                      help='Kafka topic name')
    parser.add_argument('--num-persons', type=int, default=10,
                      help='Number of person records to generate')
    parser.add_argument('--num-auctions', type=int, default=20,
                      help='Number of auction records to generate')
    parser.add_argument('--save-json', action='store_true',
                      help='Save generated data to JSON file')
    
    args = parser.parse_args()
    
    try:
        generator = NexmarkTestDataGenerator(args.bootstrap_servers, args.topic)
        test_data = generator.generate_test_dataset(args.num_persons, args.num_auctions)
        
        if args.save_json:
            with open('test_data.json', 'w') as f:
                json.dump(test_data, f, indent=2)
            logger.info("Saved test data to test_data.json")
            
        generator.send_test_data(test_data)
        
    except Exception as e:
        logger.error(f"Error in test data generation: {e}")
        raise

if __name__ == "__main__":
    main()