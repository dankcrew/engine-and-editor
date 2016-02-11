package com.unifina.data

import grails.validation.Validateable;

@Validateable
public class MongoDbConfig {
	String host
	Integer port
	String username
	String password
	String database
	String collection
	String timestampKey
	Long pollIntervalMillis
	String query

	static constraints = {
		host(blank: false)
		port(min: 0, max: 65535)
		username(blank: false)
		password(nullable: true)
		database(blank: false)
		collection(blank: false)
		timestampKey(blank: false)
		query(nullable: true)
	}

	def toMap() {
		[
		    host: host,
			port: port,
			username: username,
			password: password,
			database: database,
			collection: collection,
			timestampKey: timestampKey,
			pollIntervalMillis: pollIntervalMillis,
			query: query,
		]
	}
}
