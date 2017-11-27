package core.pages

import geb.Page
class StreamShowPage extends GrailsPage {
	static controller = "stream"
	static action = "show"
	
	static url = "$controller/$action"

	static content = {
		navbar { module NavbarModule }

		streamMenuButton(required: false) { $("#stream-menu-toggle") }
		deleteStreamButton { $("#delete-stream-button") }
		configureFieldsButton { $("#configure-fields-button") }

		streamId { $("span.stream-id") }
		
		fileInput(required:false) { $("input.dz-hidden-input") }
		
		historyStartDate(required:false) { $(".history-start-date") }
		historyEndDate(required:false) { $(".history-end-date") }
		historyDeleteDate(required:false) { $("#history-delete-date") }
		historyDeleteButton(required:false) { $("#history-delete-button") }
		noHistoryMessage(required:false) { $("#no-history-message") }
		shareButton(required: false) { $(".share-button") }

		// MongoDB-specific content
		editMongoDbButton { $("#edit-mongodb-button") }
		mongoHost { $(".mongo-host div") }
		mongoPort { $(".mongo-port div") }
		mongoUsername { $(".mongo-username div") }
		mongoPassword { $(".mongo-password div") }
		mongoDatabase { $(".mongo-database div") }
		mongoCollection { $(".mongo-collection div") }
		mongoTimestampKey { $(".mongo-timestampKey div") }
		mongoPollIntervalMillis { $(".mongo-pollIntervalMillis div") }
		mongoQuery { $(".mongo-query div") }
	}
}