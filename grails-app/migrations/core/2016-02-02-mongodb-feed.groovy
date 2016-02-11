package core
databaseChangeLog = {

	changeSet(author: "eric", id: "mongodb-feed-1") {
		addColumn(tableName: "feed") {
			column(name: "stream_listener_class", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eric", id: "mongodb-feed-2") {
		addColumn(tableName: "feed") {
			column(name: "stream_page_template", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eric", id: "mongodb-feed-4") {
		sql("UPDATE feed SET stream_listener_class = 'com.unifina.feed.kafka.KafkaStreamListener', stream_page_template = 'userStreamDetails' WHERE id = 7")
	}

	changeSet(author: "henri", id: "mongodb-feed-5") {
		sql("INSERT INTO `feed` (`id`, `version`, `backtest_feed`, `bundled_feed_files`, `cache_class`, `cache_config`, `directory`, `discovery_util_class`, `discovery_util_config`, `event_recipient_class`, `feed_config`, `key_provider_class`, `message_source_class`, `message_source_config`, `module_id`, `name`, `parser_class`, `preprocessor`, `realtime_feed`, `start_on_demand`, `timezone`, `stream_listener_class`, `stream_page_template`) VALUES (NULL, '0', 'com.unifina.feed.mongodb.MongoHistoricalFeed', NULL, NULL, NULL, NULL, NULL, NULL, 'com.unifina.feed.map.MapMessageEventRecipient', NULL, 'com.unifina.feed.mongodb.MongoKeyProvider', '', NULL, '147', 'MongoDB', '', NULL, NULL, b'1', 'UTC', 'com.unifina.feed.mongodb.MongoStreamListener', 'mongoStreamDetails');")
	}
}