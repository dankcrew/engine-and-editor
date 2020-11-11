package com.unifina.service

import com.unifina.domain.*
import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import spock.lang.Specification
import spock.lang.Unroll

@TestFor(ProductService)
@Mock([Category, Product, FreeSubscription, PaidSubscription])
class ProductServiceSpec extends Specification {
	Stream s1, s2, s3, s4
	Category category
	Product product

	void setup() {
		mockForConstraintsTests(Product)
		category = new Category(name: "Category")
		category.id = "category-id"
		category.save()
	}

	private void setupStreams() {
		s1 = new Stream(name: "stream-1")
		s2 = new Stream(name: "stream-2")
		s3 = new Stream(name: "stream-3")
		s4 = new Stream(name: "stream-4")
		[s1, s2, s3, s4].eachWithIndex { Stream stream, int i -> stream.id = "stream-${i+1}" } // assign ids
		[s1, s2, s3, s4]*.save(failOnError: true, validate: false)
	}

	private void setupProduct(Product.State state = Product.State.NOT_DEPLOYED) {
		User user = new User(
			username: "user@domain.com",
			name: "Firstname Lastname",
			password: "salasana"
		)
		user.id = 1
		user.save(failOnError: true, validate: false)
		product = new Product(
				name: "name",
				description: "description",
				ownerAddress: "0x0000000000000000000000000000000000000000",
				beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
				streams: s1 != null ? [s1, s2, s3] : [],
				pricePerSecond: 10,
				category: category,
				state: state,
				blockNumber: 40000,
				blockIndex: 30,
				owner: user
		)
		product.id = "product-id"
		product.save(failOnError: true, validate: true)
	}

	private void setupFreeProduct(Product.State state = Product.State.NOT_DEPLOYED) {
		User user = new User(
			username: "user@domain.com",
			name: "Firstname Lastname",
			password: "salasana"
		)
		user.id = 1
		user.save(failOnError: true, validate: false)
		product = new Product(
			name: "name",
			description: "description",
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			streams: s1 != null ? [s1, s2, s3] : [],
			pricePerSecond: 0,
			category: category,
			state: state,
			blockNumber: 40000,
			blockIndex: 30,
			owner: user
		)
		product.id = "product-id"
		product.save(failOnError: true, validate: true)
	}

	void "list() delegates to ApiService#list"() {
		def apiService = service.apiService = Mock(ApiService)
		def me = new User(username: "me@streamr.network")

		when:
		service.list(new ProductListParams(max: 5), me)

		then:
		1 * apiService.list(Product, {
			assert it.toMap() == new ProductListParams(max: 5, sortBy: "score", order: "desc").toMap()
			true
		}, me)
	}

	void "findById() delegates to ApiService#authorizedGetById"() {
		def apiService = service.apiService = Mock(ApiService)
		def me = new User(username: "me@streamr.network")

		when:
		service.findById("product-id", me, Permission.Operation.PRODUCT_GET)

		then:
		1 * apiService.authorizedGetById(Product, "product-id", me, Permission.Operation.PRODUCT_GET)
	}

	void "create() throws ValidationException if command object does not pass validation"() {
		when:
		service.create(new CreateProductCommand(pricePerSecond: -1), new User())
		then:
		thrown(ValidationException)
	}

	void "create() creates and returns Product with correct info and NOT_DEPLOYED state"() {
		setupStreams()
		service.permissionService = Stub(PermissionService)

		Contact contact = new Contact()
		contact.url = "https://www.fi"
		TermsOfUse termsOfUse = new TermsOfUse()
		termsOfUse.termsName = "terms link name"
		def validCommand = new CreateProductCommand(
			name: "Product",
			description: "Description of Product.",
			category: category,
			streams: [s1, s2, s3],
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			pricePerSecond: 10,
			minimumSubscriptionInSeconds: 1,
			contact: contact,
			termsOfUse: termsOfUse,
		)

		def user = new User()
		user.name = "Arnold Schwarzenegger"
		when:
		def product = service.create(validCommand, user)

		then:
		Product.findAll() == [product]

		and:
		def map = product.toMap()
		map.id == "1"
		map.type == "NORMAL"
		map.name == "Product"
		map.description == "Description of Product."
		map.imageUrl == null
		map.thumbnailUrl == null
		map.category == "category-id"
		map.streams == ["stream-1", "stream-2", "stream-3"]
		map.state == "NOT_DEPLOYED"
		map.previewStream == null
		map.previewConfigJson == null
		map.created == product.dateCreated
		map.updated == product.lastUpdated
		map.ownerAddress == "0x0000000000000000000000000000000000000000"
		map.beneficiaryAddress == "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
		map.pricePerSecond == "10"
		map.isFree == false
		map.priceCurrency == "DATA"
		map.minimumSubscriptionInSeconds == 1
		map.owner == "Arnold Schwarzenegger"
		map.contact.url == "https://www.fi"
		map.termsOfUse.termsName == "terms link name"
		product.dateCreated != null
		product.dateCreated == product.lastUpdated
	}

	void "create() invokes permissionService#systemGrant"() {
		setupStreams()
		def permissionService = service.permissionService = Mock(PermissionService)

		def validCommand = new CreateProductCommand(
			name: "Product",
			description: "Description of Product.",
			category: category,
			streams: [s1, s2, s3],
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			pricePerSecond: 10,
			minimumSubscriptionInSeconds: 1
		)
		def me = new User(username: "me@streamr.network")

		when:
		service.create(validCommand, me)
		then:
		1 * permissionService.systemGrantAll(me, _ as Product)
	}

	void "create() verifies streams via permissionService#verifyShare"() {
		setupStreams()
		def permissionService = service.permissionService = Mock(PermissionService)

		def validCommand = new CreateProductCommand(
			name: "Product",
			description: "Description of Product.",
			category: category,
			streams: [s1, s2, s3],
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			pricePerSecond: 10,
			minimumSubscriptionInSeconds: 1
		)
		def me = new User(username: "me@streamr.network")

		when:
		service.create(validCommand, me)
		then:
		1 * permissionService.verify(me, s1, Permission.Operation.STREAM_SHARE)
		1 * permissionService.verify(me, s2, Permission.Operation.STREAM_SHARE)
		1 * permissionService.verify(me, s3, Permission.Operation.STREAM_SHARE)
	}

	void "create() adds anonymous get/subscribe permission for free products streams"() {
		setupStreams()
		def permissionService = service.permissionService = Mock(PermissionService)

		def validCommand = new CreateProductCommand(
			name: "Product",
			description: "Description of Product.",
			category: category,
			streams: [s1, s2, s3],
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			pricePerSecond: 0,
			minimumSubscriptionInSeconds: 0,
		)
		def me = new User(username: "me@streamr.network")

		when:
		service.create(validCommand, me)
		then:
		1 * permissionService.systemGrantAnonymousAccess(s1, Permission.Operation.STREAM_GET)
		1 * permissionService.systemGrantAnonymousAccess(s1, Permission.Operation.STREAM_SUBSCRIBE)
		1 * permissionService.systemGrantAnonymousAccess(s2, Permission.Operation.STREAM_GET)
		1 * permissionService.systemGrantAnonymousAccess(s2, Permission.Operation.STREAM_SUBSCRIBE)
		1 * permissionService.systemGrantAnonymousAccess(s3, Permission.Operation.STREAM_GET)
		1 * permissionService.systemGrantAnonymousAccess(s3, Permission.Operation.STREAM_SUBSCRIBE)
	}

	void "create() does not save if permissionService#verifyShare throws"() {
		setupStreams()
		service.permissionService = new PermissionService()

		def validCommand = new CreateProductCommand(
			name: "Product",
			description: "Description of Product.",
			category: category,
			streams: [s1, s2, s3],
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			pricePerSecond: 10,
			minimumSubscriptionInSeconds: 1
		)
		def me = new User(username: "me@streamr.network")

		when:
		service.create(validCommand, me)

		then:
		thrown(NotPermittedException)
		Product.count() == 0
	}

	void "create() with an empty command object creates a product with default values"() {
		setupStreams()
		service.permissionService = Stub(PermissionService)

		def validCommand = new CreateProductCommand()
		def user = new User()
		user.name = "Arnold Schwarzenegger"

		when:
		def product = service.create(validCommand, user)

		then:
		Product.findAll() == [product]

		and:
		def map = product.toMap()
		map.id == "1"
		map.type == "NORMAL"
		map.name == "Untitled Product"
		map.description == null
		map.imageUrl == null
		map.thumbnailUrl == null
		map.category == null
		map.streams == []
		map.state == "NOT_DEPLOYED"
		map.previewStream == null
		map.previewConfigJson == null
		map.created == product.dateCreated
		map.updated == product.lastUpdated
		map.ownerAddress == null
		map.beneficiaryAddress == null
		map.pricePerSecond == "0"
		map.isFree == true
		map.priceCurrency == "DATA"
		map.minimumSubscriptionInSeconds == 0
		map.owner == "Arnold Schwarzenegger"
		def c = map.contact
		c.email == null
		c.url == null
		c.social1 == null
		c.social2 == null
		c.social3 == null
		c.social4 == null
		def t = map.termsOfUse
		t.redistribution == true
		t.commercialUse == true
		t.reselling == true
		t.storage == true
		t.termsUrl == null
		t.termsName == null
		product.dateCreated != null
		product.dateCreated == product.lastUpdated
	}

	void "create() can create data unions"() {
		setupStreams()
		service.permissionService = Stub(PermissionService)

		def validCommand = new CreateProductCommand(type: "DATAUNION")
		def user = new User()
		user.name = "Arnold Schwarzenegger"

		when:
		def product = service.create(validCommand, user)

		then:
		Product.findAll() == [product]

		and:
		product.toMap().type == "DATAUNION"
	}

	void "update() throws ValidationException if command object does not pass validation"() {
		when:
		service.update("product-id", new UpdateProductCommand(), new User())
		then:
		thrown(ValidationException)
	}

	void "update() verifies streams via permissionService#verifyShare"() {
		setupStreams()
		setupProduct()

		service.subscriptionService = Stub(SubscriptionService)
		service.apiService = Stub(ApiService) {
			authorizedGetById(Product, _, _, _) >> product
		}
		def permissionService = service.permissionService = Mock(PermissionService)

		def validCommand = new UpdateProductCommand(
				name: "updated name",
				description: "updated description",
				category: category,
				streams: [s2, s4],
				pricePerSecond: 20L,
				ownerAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
				beneficiaryAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
				priceCurrency: Product.Currency.DATA,
				minimumSubscriptionInSeconds: 1000
		)
		def user = new User(username: "me@streamr.network")

		when:
		service.update("product-id", validCommand, user)
		then:
		1 * permissionService.verify(user, s2, Permission.Operation.STREAM_SHARE)
		1 * permissionService.verify(user, s4, Permission.Operation.STREAM_SHARE)
	}

	void "update() revokes and grants anonymous read permission for free products streams"() {
		setupStreams()
		setupFreeProduct()

		service.subscriptionService = Stub(SubscriptionService)
		service.apiService = Stub(ApiService) {
			authorizedGetById(Product, _, _, _) >> product
		}
		def permissionService = service.permissionService = Mock(PermissionService)
		service.store = Stub(ProductStore) {
			findProductsByStream(_) >> []
		}

		def validCommand = new UpdateProductCommand(
			name: "updated name",
			description: "updated description",
			category: category,
			streams: [s2, s4],
			pricePerSecond: 0,
			ownerAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			beneficiaryAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			priceCurrency: Product.Currency.DATA,
			minimumSubscriptionInSeconds: 0
		)
		def user = new User(username: "me@streamr.network")

		when:
		service.update("product-id", validCommand, user)
		then:
		1 * permissionService.verify(user, s2, Permission.Operation.STREAM_SHARE)
		1 * permissionService.verify(user, s4, Permission.Operation.STREAM_SHARE)

		// revoke streams old permissions
		1 * permissionService.systemRevokeAnonymousAccess(s1, Permission.Operation.STREAM_GET)
		1 * permissionService.systemRevokeAnonymousAccess(s3, Permission.Operation.STREAM_GET)
		1 * permissionService.systemRevokeAnonymousAccess(s1, Permission.Operation.STREAM_SUBSCRIBE)
		1 * permissionService.systemRevokeAnonymousAccess(s3, Permission.Operation.STREAM_SUBSCRIBE)

		// grant permissions for new streams
		1 * permissionService.checkAnonymousAccess(s4, Permission.Operation.STREAM_GET) >> false
		1 * permissionService.checkAnonymousAccess(s4, Permission.Operation.STREAM_SUBSCRIBE) >> false
		1 * permissionService.systemGrantAnonymousAccess(s4, Permission.Operation.STREAM_GET)
		1 * permissionService.systemGrantAnonymousAccess(s4, Permission.Operation.STREAM_SUBSCRIBE)

		0 * permissionService._
	}

	void "update() does not grant permission if it has already been granted"() {
		setupStreams()
		setupFreeProduct()

		service.subscriptionService = Stub(SubscriptionService)
		service.apiService = Stub(ApiService) {
			authorizedGetById(Product, _, _, _) >> product
		}
		def permissionService = service.permissionService = Mock(PermissionService)
		service.store = Stub(ProductStore) {
			findProductsByStream(_) >> []
		}

		def validCommand = new UpdateProductCommand(
			name: "updated name",
			description: "updated description",
			category: category,
			streams: [s1, s2, s3, s4],
			pricePerSecond: 0,
			ownerAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			beneficiaryAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			priceCurrency: Product.Currency.DATA,
			minimumSubscriptionInSeconds: 0
		)
		def user = new User(username: "me@streamr.network")

		when:
		service.update("product-id", validCommand, user)
		then:
		1 * permissionService.verify(user, s1, Permission.Operation.STREAM_SHARE)
		1 * permissionService.verify(user, s2, Permission.Operation.STREAM_SHARE)
		1 * permissionService.verify(user, s3, Permission.Operation.STREAM_SHARE)
		1 * permissionService.verify(user, s4, Permission.Operation.STREAM_SHARE)

		// permission already granted
		1 * permissionService.checkAnonymousAccess(s4, Permission.Operation.STREAM_GET) >> true
		1 * permissionService.checkAnonymousAccess(s4, Permission.Operation.STREAM_SUBSCRIBE) >> true
		0 * permissionService.systemGrantAnonymousAccess(s4, Permission.Operation.STREAM_GET)
		0 * permissionService.systemGrantAnonymousAccess(s4, Permission.Operation.STREAM_SUBSCRIBE)

		0 * permissionService._
	}

	void "update() does not revoke permission if the stream belongs to another free product"() {
		setupStreams()
		setupFreeProduct()

		service.subscriptionService = Stub(SubscriptionService)
		service.apiService = Stub(ApiService) {
			authorizedGetById(Product, _, _, _) >> product
		}
		def permissionService = service.permissionService = Mock(PermissionService)
		service.store = Stub(ProductStore) {
			findProductsByStream(_) >> {Stream s ->
				if (s == s2) {
					return [product]
				} else {
					return [];
				}
			}
		}

		def validCommand = new UpdateProductCommand(
			name: "updated name",
			description: "updated description",
			category: category,
			streams: [s3],
			pricePerSecond: 0,
			ownerAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			beneficiaryAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			priceCurrency: Product.Currency.DATA,
			minimumSubscriptionInSeconds: 0
		)
		def user = new User(username: "me@streamr.network")

		when:
		service.update("product-id", validCommand, user)

		then:
		1 * permissionService.verify(user, s3, Permission.Operation.STREAM_SHARE)
		1 * permissionService.systemRevokeAnonymousAccess(s1, Permission.Operation.STREAM_GET)
		1 * permissionService.systemRevokeAnonymousAccess(s1, Permission.Operation.STREAM_SUBSCRIBE)
		0 * permissionService._
	}

	void "update() does not save if permissionService#verifyShare throws"() {
		setupStreams()
		setupProduct()
		service.permissionService = new PermissionService()

		def validCommand = new UpdateProductCommand(
				name: "updated name",
				description: "updated description",
				category: category,
				streams: [s2, s4]
		)

		when:
		service.update("product-id", validCommand, new User())

		then:
		thrown(NotPermittedException)
		Product.findById("product-id").name == "name"
	}

	void "update() invokes ApiService#authorizedGetById"() {
		setupProduct()

		service.subscriptionService = Stub(SubscriptionService)
		def apiService = service.apiService = Mock(ApiService)

		def validCommand = new UpdateProductCommand(
				name: "updated name",
				description: "updated description",
				category: category,
				streams: [],
				pricePerSecond: 20L,
				ownerAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
				beneficiaryAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
				priceCurrency: Product.Currency.DATA,
				minimumSubscriptionInSeconds: 1000
		)
		def user = new User(username: "me@streamr.network")

		when:
		service.update("product-id", validCommand, user)

		then:
		1 * apiService.authorizedGetById(Product, 'product-id', user, Permission.Operation.PRODUCT_EDIT) >> product
	}

	void "update() invokes subscriptionService#afterProductUpdated after Product updated"() {
		setupStreams()
		setupProduct()

		def subscriptionService = service.subscriptionService = Mock(SubscriptionService)
		service.apiService = Stub(ApiService) {
			authorizedGetById(Product, _, _, _) >> product
		}
		service.permissionService = Stub(PermissionService)

		def validCommand = new UpdateProductCommand(
			name: "updated name",
			description: "updated description",
			category: category,
			streams: [s2, s4],
			pricePerSecond: 20L,
			ownerAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			beneficiaryAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
			priceCurrency: Product.Currency.DATA,
			minimumSubscriptionInSeconds: 1000
		)
		def user = new User(username: "me@streamr.network")

		when:
		service.update("product-id", validCommand, user)
		then:
		1 * subscriptionService.afterProductUpdated(product)
	}

	void "update() updates and returns Product with correct info"() {
		setupStreams()
		setupProduct()

		Category category2 = new Category(name: "Category 2")
		category2.id = "category2-id"
		category2.save()

		service.subscriptionService = Stub(SubscriptionService)
		service.apiService = Stub(ApiService) {
			authorizedGetById(Product, _, _, _) >> product
		}
		service.permissionService = Stub(PermissionService)

		def contact = new Contact()
		contact.email = "email@address.org"
		contact.url = "https://site.com"
		contact.social1 = "https://twitter.com"
		contact.social2 = "https://facebook.com"
		contact.social3 = "https://telegram.com"
		contact.social4 = "https://linkedin.com"

		def terms = new TermsOfUse()
		terms.redistribution = false
		terms.commercialUse = false
		terms.reselling = false
		terms.storage = false
		terms.termsUrl = "https://www.site.org"
		terms.termsName = "legal terms for site.org"

		def validCommand = new UpdateProductCommand(
				name: "updated name",
				description: "updated description",
				category: category2,
				streams: [s2, s4],
				pricePerSecond: 20L,
				ownerAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
				beneficiaryAddress: "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
				priceCurrency: Product.Currency.DATA,
				minimumSubscriptionInSeconds: 1000,
				contact: contact,
				termsOfUse: terms,
		)

		when:
		def updatedProduct = service.update("product-id", validCommand, new User())

		then:
		Product.findById("product-id").toMap() == updatedProduct.toMap()

		and:
		def map = updatedProduct.toMap()
		map.id == "product-id"
		map.type == "NORMAL"
		map.name == "updated name"
		map.description == "updated description"
		map.imageUrl == null
		map.thumbnailUrl == null
		map.category == "category2-id"
		map.streams == ["stream-2", "stream-4"]
		map.state == "NOT_DEPLOYED"
		map.previewStream == null
		map.previewConfigJson == null
		map.created == product.dateCreated
		map.updated == product.lastUpdated
		map.ownerAddress == "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
		map.beneficiaryAddress == "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
		map.pricePerSecond == "20"
		map.isFree == false
		map.priceCurrency == "DATA"
		map.minimumSubscriptionInSeconds == 1000
		map.owner == "Firstname Lastname"
		product.dateCreated < product.lastUpdated

		def c = map.contact
		c.email == "email@address.org"
		c.url == "https://site.com"
		c.social1 == "https://twitter.com"
		c.social2 == "https://facebook.com"
		c.social3 == "https://telegram.com"
		c.social4 == "https://linkedin.com"

		def t = map.termsOfUse
		t.redistribution == false
		t.commercialUse == false
		t.reselling == false
		t.storage == false
		t.termsUrl == "https://www.site.org"
		t.termsName == "legal terms for site.org"
	}

	void "addStreamToProduct() verifies Stream via PermissionService#verify"() {
		setupStreams()
		setupProduct()
		service.subscriptionService = Stub(SubscriptionService)
		service.permissionService = Mock(PermissionService)
		def user = new User()
		when:
		service.addStreamToProduct(product, s4, user)
		then:
		1 * service.permissionService.verify(user, s4, Permission.Operation.STREAM_SHARE)
		0 * service.permissionService._
	}

	void "addStreamToProduct() adds Stream to Product"() {
		setupStreams()
		setupProduct()
		assert !product.streams.contains(s4)

		service.subscriptionService = Stub(SubscriptionService)
		service.permissionService = Stub(PermissionService)
		def user = new User()

		when:
		service.addStreamToProduct(product, s4, user)
		then:
		product.streams.contains(s4)
	}

	void "addStreamToProduct() grants anonymous read permission for free products stream"() {
		setupStreams()
		setupFreeProduct()
		assert !product.streams.contains(s4)

		service.subscriptionService = Stub(SubscriptionService)
		service.permissionService = Mock(PermissionService)
		def user = new User()

		when:
		service.addStreamToProduct(product, s4, user)
		then:
		1 * service.permissionService.verify(user, s4, Permission.Operation.STREAM_SHARE)
		1 * service.permissionService.systemGrantAnonymousAccess(s4, Permission.Operation.STREAM_GET)
		1 * service.permissionService.systemGrantAnonymousAccess(s4, Permission.Operation.STREAM_SUBSCRIBE)
		0 * service.permissionService._
	}

	void "addStreamToProduct() invokes subscriptionService#afterProductUpdated"() {
		setupStreams()
		setupProduct()
		def subscriptionService = service.subscriptionService = Mock(SubscriptionService)
		service.permissionService = Stub(PermissionService)
		def user = new User()

		when:
		service.addStreamToProduct(product, s4, user)
		then:
		1 * subscriptionService.afterProductUpdated(product)
	}

	void "removeStreamFromProduct() removes Stream from Product"() {
		setupStreams()
		setupProduct()
		service.subscriptionService = Stub(SubscriptionService)
		assert product.streams.contains(s1)

		when:
		service.removeStreamFromProduct(product, s1)
		then:
		!product.streams.contains(s1)
	}

	void "removeStreamFromProduct() revokes anonymous read access from free products stream"() {
		setupStreams()
		setupFreeProduct()
		service.subscriptionService = Stub(SubscriptionService)
		service.permissionService = Mock(PermissionService)
		assert product.streams.contains(s1)

		when:
		service.removeStreamFromProduct(product, s1)
		then:
		1 * service.permissionService.systemRevokeAnonymousAccess(s1, Permission.Operation.STREAM_GET)
		1 * service.permissionService.systemRevokeAnonymousAccess(s1, Permission.Operation.STREAM_SUBSCRIBE)
		0 * service.permissionService._
	}

	void "removeStreamFromProduct() invokes subscriptionService#afterProductUpdated"() {
		setupStreams()
		setupProduct()
		def subscriptionService = service.subscriptionService = Mock(SubscriptionService)

		when:
		service.removeStreamFromProduct(product, s1)
		then:
		1 * subscriptionService.afterProductUpdated(product)
	}

	@Unroll
	void "transitionToDeploying() throws InvalidStateTransitionException if Product.state == #state"(Product.State state) {
		setupProduct(state)
		when:
		service.transitionToDeploying(product)
		then:
		thrown(InvalidStateTransitionException)
		where:
		state << [Product.State.DEPLOYING, Product.State.UNDEPLOYING, Product.State.DEPLOYED]
	}

	void "transitionToDeploying() transitions Product from NOT_DEPLOYED to DEPLOYING"() {
		setupProduct(Product.State.NOT_DEPLOYED)
		when:
		service.transitionToDeploying(product)
		then:
		product.state == Product.State.DEPLOYING
	}

	@Unroll
	void "transitionToUndeploying() throws InvalidStateTransitionException if Product.state == #state"(Product.State state) {
		setupProduct(state)
		when:
		service.transitionToUndeploying(product)
		then:
		thrown(InvalidStateTransitionException)
		where:
		state << [Product.State.DEPLOYING, Product.State.UNDEPLOYING, Product.State.NOT_DEPLOYED]
	}

	void "transitionToUndeploying() transitions Product from DEPLOYED to UNDEPLOYING"() {
		setupProduct(Product.State.DEPLOYED)
		when:
		service.transitionToUndeploying(product)
		then:
		product.state == Product.State.UNDEPLOYING
	}

	@Unroll
	void "markAsUndeployed() throws InvalidStateTransitionException if Product.state == #state"(Product.State state) {
		setupProduct(state)
		def command = new ProductUndeployedCommand(blockNumber: 50000, blockIndex: 15)
		when:
		service.markAsUndeployed(product, command, null)
		then:
		thrown(InvalidStateTransitionException)
		where:
		state << [Product.State.DEPLOYING, Product.State.NOT_DEPLOYED]
	}

	void "markAsUndeployed() throws NotPermittedException if user is not devops"() {
		setupProduct(Product.State.UNDEPLOYING)
		def command = new ProductUndeployedCommand(blockNumber: 50000, blockIndex: 15)

		when:
		service.markAsUndeployed(product, command, Stub(User) {
			isDevOps() >> false
		})
		then:
		thrown(NotPermittedException)
	}

	void "markAsUndeployed() returns false if command is stale"() {
		setupProduct(Product.State.UNDEPLOYING)
		service.permissionService = new PermissionService()

		def command = new ProductUndeployedCommand(blockNumber: 30000, blockIndex: 15)

		when:
		boolean result = service.markAsUndeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		!result
	}

	void "markAsUndeployed() does not invoke permissionService#systemRevokeAnonymousAccess if command is stale"() {
		setupProduct(Product.State.DEPLOYED)
		def permissionService = service.permissionService = Mock(PermissionService)

		def command = new ProductUndeployedCommand(blockNumber: 30000, blockIndex: 15)

		when:
		service.markAsUndeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		0 * permissionService.systemRevokeAnonymousAccess(_)
	}

	void "markAsUndeployed() does not transition Product to NOT_DEPLOYED if command is stale"() {
		setupProduct(Product.State.DEPLOYED)
		def permissionService = service.permissionService = Mock(PermissionService)

		def command = new ProductUndeployedCommand(blockNumber: 30000, blockIndex: 15)

		when:
		service.markAsUndeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		product.state != Product.State.NOT_DEPLOYED
	}

	void "markAsUndeployed() returns true if command not stale"() {
		setupProduct(Product.State.UNDEPLOYING)
		service.permissionService = new PermissionService()

		def command = new ProductUndeployedCommand(blockNumber: 50000, blockIndex: 15)

		when:
		boolean result = service.markAsUndeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		result
	}

	@Unroll
	void "markAsUndeployed() transitions Product from #state to NOT_DEPLOYED"(Product.State state) {
		setupProduct(state)
		service.permissionService = new PermissionService()

		def command = new ProductUndeployedCommand(blockNumber: 50000, blockIndex: 15)

		when:
		service.markAsUndeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		product.state == Product.State.NOT_DEPLOYED

		where:
		state << [Product.State.DEPLOYED, Product.State.UNDEPLOYING]
	}

	void "markAsUndeployed() invokes permissionService#systemRevokeAnonymousAccess"() {
		setupProduct(Product.State.UNDEPLOYING)
		def permissionService = service.permissionService = Mock(PermissionService)

		def command = new ProductUndeployedCommand(blockNumber: 50000, blockIndex: 15)

		when:
		service.markAsUndeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		1 * permissionService.systemRevokeAnonymousAccess(product, Permission.Operation.PRODUCT_GET)
	}

	void "markAsDeployed() throws ValidationException if command object does not pass validation"() {
		setupProduct()
		when:
		service.markAsDeployed(product, new ProductDeployedCommand(), new User())
		then:
		thrown(ValidationException)
	}

	void "markAsDeployed() throws InvalidStateTransitionException if Product.state == UNDEPLOYING"() {
		setupProduct(Product.State.UNDEPLOYING)

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 50000,
				blockIndex: 10
		)

		when:
		service.markAsDeployed(product, command, new User())
		then:
		thrown(InvalidStateTransitionException)
	}

	void "markAsDeployed() throws NotPermittedException if user is not devops"() {
		setupProduct()

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 50000,
				blockIndex: 10
		)

		when:
		service.markAsDeployed(product, command, Stub(User) {
			isDevOps() >> false
		})
		then:
		thrown(NotPermittedException)
	}

	void "markAsDeployed() returns false if command object is stale"() {
		setupProduct()
		service.permissionService = Stub(PermissionService)

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 30000,
				blockIndex: 10
		)

		when:
		boolean result = service.markAsDeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		!result
	}

	void "markAsDeployed() does not invoke permissionService#systemGrantAnonymousAccess if command object is stale"() {
		setupProduct()
		def permissionService = service.permissionService = Mock(PermissionService)

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 30000,
				blockIndex: 10
		)

		when:
		boolean result = service.markAsDeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		0 * permissionService.systemGrantAnonymousAccess(_)
	}

	void "markAsDeployed() does not update Product if command object is stale"() {
		setupProduct()
		service.permissionService = Stub(PermissionService)

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 30000,
				blockIndex: 10
		)

		when:
		service.markAsDeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		product.state == Product.State.NOT_DEPLOYED
		product.ownerAddress == "0x0000000000000000000000000000000000000000"
	}

	void "markAsDeployed() returns true if command not stale"() {
		setupProduct()
		service.permissionService = Stub(PermissionService)

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 50000,
				blockIndex: 10
		)

		when:
		boolean result = service.markAsDeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		result
	}

	void "markAsDeployed() transitions Product to DEPLOYED and updates Blockchain-related information"() {
		setupProduct()
		service.permissionService = Stub(PermissionService)

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 50000,
				blockIndex: 10
		)

		when:
		service.markAsDeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		Product.findById("product-id").toMap() == product.toMap()

		and:
		def map = product.toMap()
		map.id ==  "product-id"
		map.type == "NORMAL"
		map.name == "name"
		map.description == "description"
		map.imageUrl == null
		map.thumbnailUrl == null
		map.category == "category-id"
		map.streams == []
		map.previewStream == null
		map.previewConfigJson == null
		map.created == product.dateCreated
		map.updated == product.lastUpdated

		// changes below
		map.state == "DEPLOYED"
		map.ownerAddress == "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
		map.beneficiaryAddress == "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
		map.pricePerSecond == "2"
		map.isFree == false
		map.priceCurrency == "USD"
		map.minimumSubscriptionInSeconds == 600
		map.owner == "Firstname Lastname"
	}

	void "markAsDeployed() grants public access to Product via permissionService#systemGrantAnonymousAccess"() {
		setupProduct()
		def permissionService = service.permissionService = Mock(PermissionService)

		def command = new ProductDeployedCommand(
				ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
				beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
				pricePerSecond: 2,
				priceCurrency: Product.Currency.USD,
				minimumSubscriptionInSeconds: 600,
				blockNumber: 50000,
				blockIndex: 10
		)

		when:
		def product = service.markAsDeployed(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		1 * permissionService.systemGrantAnonymousAccess(product, Permission.Operation.PRODUCT_GET)
	}

	void "updatePricing() updates product price etc"() {
		setupProduct(Product.State.DEPLOYED)
		service.permissionService = new PermissionService()

		def command = new SetPricingCommand(
			ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
			beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
			pricePerSecond: 2,
			priceCurrency: Product.Currency.USD,
			minimumSubscriptionInSeconds: 600,
			blockNumber: 50000,
			blockIndex: 10
		)

		when:
		service.updatePricing(product, command, Stub(User) {
			isDevOps() >> true
		})

		then:
		product.ownerAddress == "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
		product.beneficiaryAddress == "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
		product.pricePerSecond == 2
		product.priceCurrency == Product.Currency.USD
		product.minimumSubscriptionInSeconds == 600
		product.blockNumber == 50000
		product.blockIndex == 10
	}

	void "updatePricing() throws NotPermittedException if user is not devops"() {
		setupProduct()

		def command = new SetPricingCommand(
			ownerAddress: "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
			beneficiaryAddress: "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
			pricePerSecond: 2,
			priceCurrency: Product.Currency.USD,
			minimumSubscriptionInSeconds: 600,
			blockNumber: 50000,
			blockIndex: 10
		)

		when:
		service.updatePricing(product, command, Stub(User) {
			isDevOps() >> false
		})
		then:
		thrown(NotPermittedException)
	}

	void "add stream to product and grant data union product stream permissions"() {
		setup:
		service.subscriptionService = Stub(SubscriptionService)
		service.permissionService = Mock(PermissionService)
		service.dataUnionJoinRequestService = Mock(DataUnionJoinRequestService)
		setupStreams()
		User user = new User(
			username: "user@domain.com",
			name: "Firstname Lastname",
			password: "salasana"
		)
		user.id = 1
		user.save(failOnError: true, validate: false)
		Product product = new Product(
			name: "name",
			description: "description",
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			streams: s1 != null ? [s1, s2, s3] : [],
			pricePerSecond: 10,
			category: category,
			state: Product.State.NOT_DEPLOYED,
			blockNumber: 40000,
			blockIndex: 30,
			owner: user,
			type: Product.Type.DATAUNION,
		)
		product.id = "product-id"
		product.save(failOnError: true, validate: true)

		when:
		service.addStreamToProduct(product, s1, user)
		then:
		1 * service.dataUnionJoinRequestService.findMembers("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF") >> [user]
		1 * service.permissionService.check(user, s1, Permission.Operation.STREAM_PUBLISH) >> false
		1 * service.permissionService.systemGrant(user, s1, Permission.Operation.STREAM_PUBLISH)
	}

	void "remove stream from product and revoke data union stream permissions"() {
		setup:
		service.subscriptionService = Stub(SubscriptionService)
		service.permissionService = Mock(PermissionService)
		service.dataUnionJoinRequestService = Mock(DataUnionJoinRequestService)
		setupStreams()
		User user = new User(
			username: "user@domain.com",
			name: "Firstname Lastname",
			password: "salasana"
		)
		user.id = 1
		user.save(failOnError: true, validate: false)
		Product product = new Product(
			name: "name",
			description: "description",
			ownerAddress: "0x0000000000000000000000000000000000000000",
			beneficiaryAddress: "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			streams: s1 != null ? [s1, s2, s3] : [],
			pricePerSecond: 10,
			category: category,
			state: Product.State.NOT_DEPLOYED,
			blockNumber: 40000,
			blockIndex: 30,
			owner: user,
			type: Product.Type.DATAUNION,
		)
		product.id = "product-id"
		product.save(failOnError: true, validate: true)

		when:
		service.removeStreamFromProduct(product, s1)
		then:
		1 * service.dataUnionJoinRequestService.findMembers("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF") >> [user]
		1 * service.permissionService.check(user, s1, Permission.Operation.STREAM_PUBLISH) >> true
		1 * service.permissionService.systemRevoke(user, s1, Permission.Operation.STREAM_PUBLISH)
	}
}
