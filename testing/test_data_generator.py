import json
import logging
import argparse
from datetime import datetime, timedelta
from kafka import KafkaProducer
from kafka.errors import KafkaError
from typing import Dict, List
import time

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class NexmarkTestDataGenerator:
    def __init__(self, bootstrap_servers: str, topic: str):
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda x: json.dumps(x).encode('utf-8'),
            retries=5,
            acks='all'
        )
        self.topic = topic
        self.base_time = datetime.now()
        logger.info(f"Initialized producer for topic {topic} at {bootstrap_servers}")

    def generate_person(self, id: int) -> Dict:
        states = ["OR", "ID", "CA"]
        cities = {
            "OR": ["Portland", "Salem", "Eugene"],
            "ID": ["Boise", "Nampa", "Meridian"],
            "CA": ["San Francisco", "Los Angeles", "San Diego"]
        }
        state = states[id % 3]
        return {
            "event_type": 0,
            "person": {
                "id": id,
                "name": f"Person_{id}",
                "emailAddress": f"person_{id}@test.com",
                "creditCard": f"CC_{id}",
                "city": cities[state][id % 3],
                "state": state,
                "dateTime": (self.base_time + timedelta(seconds=id)).isoformat(),
                "extra": "test_data"
            }
        }

    def generate_auction(self, id: int, seller_id: int) -> Dict:
        categories = [10, 20, 30]  # Category 10 is what we're testing for
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
                "category": categories[id % 3],
                "extra": "test_data"
            }
        }

    def generate_test_dataset(self, num_persons: int, num_auctions: int) -> List[Dict]:
        """Generate a deterministic test dataset"""
        test_data = []
        
        # Generate persons first to ensure sellers exist
        logger.info(f"Generating {num_persons} person records...")
        for i in range(num_persons):
            test_data.append(self.generate_person(i))
            
        # Generate auctions with known sellers
        logger.info(f"Generating {num_auctions} auction records...")
        for i in range(num_auctions):
            seller_id = i % num_persons  # Ensure seller exists
            test_data.append(self.generate_auction(i, seller_id))
            
        return test_data

    def send_test_data(self, test_data: List[Dict], delay_ms: int = 100):
        """Send test data to Kafka topic with configurable delay"""
        logger.info(f"Starting to send {len(test_data)} events to topic {self.topic}")
        success_count = 0
        error_count = 0
        
        for event in test_data:
            try:
                future = self.producer.send(self.topic, event)
                future.get(timeout=10)  # Wait for the send to complete
                success_count += 1
                if delay_ms > 0:
                    time.sleep(delay_ms / 1000)  # Convert ms to seconds
            except KafkaError as e:
                logger.error(f"Error sending event: {e}")
                error_count += 1
                
        self.producer.flush()
        logger.info(f"Completed sending events. Success: {success_count}, Errors: {error_count}")

def main():
    parser = argparse.ArgumentParser(description='Generate and send test data to Kafka topic')
    parser.add_argument('--bootstrap-servers', default='kafka1:19092',
                      help='Kafka bootstrap servers')
    parser.add_argument('--topic', default='nexmark-events',
                      help='Kafka topic name')
    parser.add_argument('--num-persons', type=int, default=10,
                      help='Number of person records to generate')
    parser.add_argument('--num-auctions', type=int, default=20,
                      help='Number of auction records to generate')
    parser.add_argument('--delay-ms', type=int, default=100,
                      help='Delay between messages in milliseconds')
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
            
        generator.send_test_data(test_data, args.delay_ms)
        
    except Exception as e:
        logger.error(f"Error in test data generation: {e}")
        raise

if __name__ == "__main__":
    main()