package com.unifina.service

import com.unifina.BeanMockingSpecification
import com.unifina.domain.*
import com.unifina.domain.Permission.Operation
import grails.gsp.PageRenderer
import grails.plugin.mail.MailService
import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin

import java.security.AccessControlException

/*
	If you get weird test failures, it may be due to spotty GORM and mocked criteria queries.
	You might want to try PermissionServiceIntegrationSpec instead.
 */
@TestMixin(GrailsUnitTestMixin)
@TestFor(PermissionService)
@Mock([User, SignupInvite, Module, Permission, Dashboard, Canvas])
class PermissionServiceSpec extends BeanMockingSpecification {

	User me, anotherUser, stranger

	Dashboard dashAllowed, dashRestricted, dashOwned, dashPublic
	Permission dashReadPermission, dashAnonymousReadPermission

	StreamService streamService

    def setup() {
		// Users
		me = new User(username: "me").save(validate:false)
		anotherUser = new User(username: "him").save(validate:false)
		stranger = new User(username: "stranger").save(validate:false)

		// Dashboards
		dashAllowed = new Dashboard(id: "allowed", name:"allowed").save(validate:false)
		dashRestricted = new Dashboard(id: "restricted", name:"restricted").save(validate:false)
		dashOwned = new Dashboard(id: "owned", name:"owned").save(validate:false)
		dashPublic = new Dashboard(id: "public", name:"public").save(validate:false)

		service.systemGrantAll(anotherUser, dashAllowed)
		service.systemGrantAll(me, dashOwned)
		service.systemGrantAll(anotherUser, dashRestricted)
		service.systemGrantAll(anotherUser, dashPublic)

		Canvas canvas = new Canvas().save(validate: false, flush: true)
		service.systemGrantAll(anotherUser, canvas)

		// Set up the Permissions to the allowed resources
		dashReadPermission = service.systemGrant(me, dashAllowed, Operation.DASHBOARD_GET)
		dashAnonymousReadPermission = service.systemGrantAnonymousAccess(dashPublic, Operation.DASHBOARD_GET)

		streamService = mockBean(StreamService, Mock(StreamService))
    }

	void "test setup"() {
		expect:
		User.count() == 3
		Dashboard.count() == 4
		Canvas.count() == 1
		Permission.count() == 28

		and: "anotherUser has an invitation"
	}

	void "access denied to non-permitted Dashboard"() {
		expect:
		!service.check(me, dashRestricted, Permission.Operation.DASHBOARD_GET)
	}

	void "non-permitted third-parties have no access to resources"() {
		expect:
		!service.check(stranger, dashAllowed, Permission.Operation.DASHBOARD_GET)
		!service.check(stranger, dashRestricted, Permission.Operation.DASHBOARD_GET)
		!service.check(stranger, dashOwned, Permission.Operation.DASHBOARD_GET)
	}

	void "canRead returns false on bad inputs"() {
		expect:
		!service.check(null, dashAllowed, Permission.Operation.DASHBOARD_GET)
		!service.check(me, new Dashboard(), Permission.Operation.DASHBOARD_GET)
		!service.check(me, null, Permission.Operation.DASHBOARD_GET)
	}

	void "getPermissionsTo(resource, userish) returns permissions for single user"() {
		expect:
		service.getPermissionsTo(dashOwned, me).size() == Operation.dashboardOperations().size()
		service.getPermissionsTo(dashOwned, anotherUser) == []
		service.getPermissionsTo(dashOwned, stranger) == []
		service.getPermissionsTo(dashOwned, null) == []
		service.getPermissionsTo(dashAllowed, me)[0].operation == Operation.DASHBOARD_GET
		service.getPermissionsTo(dashAllowed, anotherUser).size() == 5
		service.getPermissionsTo(dashAllowed, stranger) == []
		service.getPermissionsTo(dashAllowed, null) == []
		service.getPermissionsTo(dashRestricted, me) == []
		service.getPermissionsTo(dashRestricted, anotherUser).size() == 5
		service.getPermissionsTo(dashRestricted, stranger) == []
		service.getPermissionsTo(dashRestricted, null) == []
		service.getPermissionsTo(dashPublic, me)[0].operation == Operation.DASHBOARD_GET
		service.getPermissionsTo(dashPublic, anotherUser).size() == 6
		service.getPermissionsTo(dashPublic, stranger)[0].operation == Operation.DASHBOARD_GET
		service.getPermissionsTo(dashPublic, null)[0].operation == Operation.DASHBOARD_GET
	}

	void "get throws exceptions on invalid resource"() {
		when:
		service.get(java.lang.Object, me, Permission.Operation.DASHBOARD_GET)
		then:
		thrown(IllegalArgumentException)

		when:
		service.get(null, me, Permission.Operation.DASHBOARD_GET)
		then:
		thrown(NullPointerException)
	}

	void "grant and revoke throw for non-'share'-access users"() {
		when:
		service.grant(me, dashAllowed, stranger, Permission.Operation.DASHBOARD_GET)
		then:
		thrown AccessControlException

		when:
		service.revoke(stranger, dashRestricted, me, Permission.Operation.DASHBOARD_GET)
		then:
		thrown AccessControlException
	}

	void "systemRevoke() on a stream also revokes the parent permissions"() {
		User publisher = new User()
		publisher.id = 7L
		Stream pub = new Stream(name: "publisher")
		pub.id = "publisher"
		pub.save(failOnError: true, validate: false)
		User subscriber = new User(username: "0x26e1ae3f5efe8a01eca8c2e9d3c32702cf4bead6").save(failOnError: true, validate: false)
		Stream sub = new Stream(name: subscriber.username)
		sub.id = subscriber.username
		sub.save(failOnError: true, validate: false)
		Stream stream = new Stream()
		stream.id = "stream"
		setup:
		new Permission(user: publisher, stream: stream, operation: Operation.STREAM_PUBLISH).save(failOnError: true, validate: false)

		Permission parent = new Permission(user: subscriber, stream: stream, operation: Operation.STREAM_SUBSCRIBE).save(failOnError: true, validate: false)
		new Permission(user: subscriber, stream: pub, operation: Operation.STREAM_PUBLISH, parent: parent).save(failOnError: true, validate: false)
		new Permission(user: publisher, stream: sub, operation: Operation.STREAM_PUBLISH, parent: parent).save(failOnError: true, validate: false)
		when:
		service.systemRevoke(subscriber, stream, Operation.STREAM_SUBSCRIBE)
		then:
		!service.check(subscriber, stream, Permission.Operation.STREAM_SUBSCRIBE)
		!service.check(subscriber, pub, Permission.Operation.STREAM_PUBLISH)
		!service.check(publisher, sub, Permission.Operation.STREAM_PUBLISH)
	}

	void "stranger can read public resources with anonymous read access"() {
		expect: "... but not more than read"
		service.check(stranger, dashPublic, Permission.Operation.DASHBOARD_GET)
		!service.check(stranger, dashPublic, Permission.Operation.DASHBOARD_EDIT)
		!service.check(stranger, dashPublic, Permission.Operation.DASHBOARD_SHARE)
	}

	void "verify does not throw if permission exists"() {
		when:
		service.verify(stranger, dashPublic, Operation.DASHBOARD_GET)
		then:
		notThrown(NotPermittedException)
	}

	void "verify throws if permission does not exist"() {
		when:
		service.verify(stranger, dashPublic, Operation.DASHBOARD_EDIT)
		then:
		def e = thrown(NotPermittedException)
		e.message == "stranger does not have permission to dashboard_edit Dashboard (id 4)"
	}

	void "systemRevokeAnonymousAccess() revokes anonymous access on a resource"() {
		assert Permission.exists(dashAnonymousReadPermission.id)
		assert service.check(null, dashPublic, Permission.Operation.DASHBOARD_GET)

		when:
		service.systemRevokeAnonymousAccess(dashPublic, Operation.DASHBOARD_GET)

		then:
		!Permission.exists(dashAnonymousReadPermission.id)
		!service.check(null, dashPublic, Permission.Operation.DASHBOARD_GET)
	}

	void "check() returns false if permission with endsAt set in past"() {
		def p = service.systemGrant(stranger, dashOwned, Operation.DASHBOARD_GET)
		p.endsAt = new Date(0)
		p.save(failOnError: true)

		expect:
		!service.check(stranger, dashOwned, Operation.DASHBOARD_GET)
	}

	void "check() returns true if permission with endsAt set in future"() {
		def p = service.systemGrant(stranger, dashOwned, Operation.DASHBOARD_GET)
		p.endsAt = new Date(System.currentTimeMillis() + 60000)
		p.save(failOnError: true)

		expect:
		service.check(stranger, dashOwned, Operation.DASHBOARD_GET)
	}

	void "cleanUpExpiredPermissions() deletes permissions that already ended"() {
		User testUser = new User(username: "testUser").save(validate:false)
		Stream testStream = new Stream(name: "testStream")
		testStream.id = "testStream"
		testStream.save(validate: false)

		assert Permission.findAllByStream(testStream).size() == 0

		when:
		Permission p1 = service.systemGrant(testUser, testStream, Operation.STREAM_GET)
		p1.endsAt = new Date(0)
		p1.save(failOnError: true)
		Permission p2 = service.systemGrant(testUser, testStream, Operation.STREAM_EDIT)
		p2.endsAt = new Date(System.currentTimeMillis() + 60000)
		p2.save(failOnError: true)

		then:
		Permission.findAllByStream(testStream).size() == 2

		when:
		service.cleanUpExpiredPermissions()

		then:
		Permission.findAllByStream(testStream).size() == 1
		!service.check(testUser, testStream, Operation.STREAM_GET)
		service.check(testUser, testStream, Permission.Operation.STREAM_EDIT)
	}

	def newCanvas = { String id ->
		def c = new Canvas()
		c.id = id
		return c.save(validate: false)
	}

	void "save sends an email for read permission"() {
		setup:
		service.mailService = Mock(MailService)
		service.groovyPageRenderer = Mock(PageRenderer)
		User me = new User(id: 1, username: "me@me.net").save(validate: false)
		User other = new User(id: 2, username: "permission@recipient.net").save(validate: false)
		Canvas canvasOwned = newCanvas("own")
		Resource res = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		Operation op = Operation.CANVAS_GET
		String targetUsername = other.username
		String sharer = me.username
		String recipient = other.username
		String subjectTemplate = "%USER% wants to share a %RESOURCE% with you via Streamr Core"
		EmailMessage msg = new EmailMessage(sharer, recipient, subjectTemplate, res)
		service.systemGrant(me, canvasOwned, Operation.CANVAS_SHARE)
		when:
		service.savePermissionAndSendShareResourceEmail(apiUser, op, targetUsername, msg)
		then:
		service.check(other, canvasOwned, Operation.CANVAS_GET)
		1 * service.groovyPageRenderer.render(_) >> "<html>email</html>"
		1 * service.mailService.sendMail { _ }
	}

	void "save does not send an email for write permission"() {
		setup:
		service.mailService = Mock(MailService)
		service.groovyPageRenderer = Mock(PageRenderer)
		User me = new User(id: 1, username: "me@me.net").save(validate: false)
		User other = new User(id: 2, username: "permission@recipient.net").save(validate: false)
		Canvas canvasOwned = newCanvas("own")
		Resource res = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		Operation op = Operation.CANVAS_EDIT
		String targetUsername = other.username
		String sharer = me.username
		String recipient = other.username
		String subjectTemplate = "%USER% wants to share a %RESOURCE% with you via Streamr Core"
		EmailMessage msg = new EmailMessage(sharer, recipient, subjectTemplate, res)
		service.systemGrant(me, canvasOwned, Operation.CANVAS_SHARE)
		when:
		service.savePermissionAndSendShareResourceEmail(apiUser, op, targetUsername, msg)
		then:
		service.check(other, canvasOwned, Operation.CANVAS_EDIT)
		0 * service.groovyPageRenderer.render(_) >> "<html>email</html>"
		0 * service.mailService.sendMail { _ }
	}

	void "save does not send an email for share permission"() {
		setup:
		service.mailService = Mock(MailService)
		service.groovyPageRenderer = Mock(PageRenderer)
		User me = new User(id: 1, username: "me@me.net").save(validate: false)
		User other = new User(id: 2, username: "permission@recipient.net").save(validate: false)
		Canvas canvasOwned = newCanvas("own")
		Resource res = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		Operation op = Operation.CANVAS_SHARE
		String sharer = me.username
		String recipient = other.username
		String subjectTemplate = "%USER% wants to share a %RESOURCE% with you via Streamr Core"
		EmailMessage msg = new EmailMessage(sharer, recipient, subjectTemplate, res)
		String targetUsername = other.username
		service.systemGrant(me, canvasOwned, Operation.CANVAS_SHARE)
		when:
		service.savePermissionAndSendShareResourceEmail(apiUser, op, targetUsername, msg)
		then:
		service.check(other, canvasOwned, Operation.CANVAS_SHARE)
		0 * service.groovyPageRenderer.render(_) >> "<html>email</html>"
		0 * service.mailService.sendMail { _ }
	}

	void "save() creates a new user with permission if unknown ethereum address provided"() {
		setup:
		service.mailService = Mock(MailService)
		service.groovyPageRenderer = Mock(PageRenderer)
		EthereumIntegrationKeyService ethereumIntegrationKeyService = mockBean(EthereumIntegrationKeyService, Mock(EthereumIntegrationKeyService))
		String ethUserUsername = "0xa50E97f6a98dD992D9eCb8207c2Aa58F54970729"
        User createdEthUser = new User(username: ethUserUsername, name: "Ethereum User")
		createdEthUser.save(validate: true, failOnError: true)
		Canvas canvasOwned = newCanvas("own")
		Resource res = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		Operation op = Operation.CANVAS_GET
		service.systemGrant(me, canvasOwned, Operation.CANVAS_SHARE)
		when:
		service.savePermissionForEthereumAccount(ethUserUsername, apiUser, op, res, SignupMethod.UNKNOWN)
		then:
		1 * ethereumIntegrationKeyService.getOrCreateFromEthereumAddress(ethUserUsername, SignupMethod.UNKNOWN) >> createdEthUser
		0 * service.groovyPageRenderer.render(_) >> "<html>email</html>"
		0 * service.mailService.sendMail { _ }
		service.check(createdEthUser, canvasOwned, op)
	}

	void "save sends an email if the user has no account yet"() {
		setup:
		service.mailService = Mock(MailService)
		service.groovyPageRenderer = Mock(PageRenderer)
		service.signupCodeService = Mock(SignupCodeService)
		User me = new User(id: 1, username: "me@me.net").save(validate: false)
		User other = new User(id: 2, username: "permission@recipient.net").save(validate: false)
		Canvas canvasOwned = newCanvas("own")
		Resource res = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		Operation op = Operation.CANVAS_GET
		String targetUsername = other.username
		String sharer = me.username
		String recipient = other.username
		String subjectTemplate = "%USER% wants to share a %RESOURCE% with you via Streamr Core"
		EmailMessage msg = new EmailMessage(sharer, recipient, subjectTemplate, res)
		SignupInvite invite = new SignupInvite(
			code: "x",
			email: recipient,
			used: false,
			sent: false,
		)
		service.systemGrant(apiUser, canvasOwned, Operation.CANVAS_SHARE)
		when:
		service.savePermissionAndSendEmailShareResourceInvite(apiUser, recipient, op, msg)
		then:
		service.check(invite, canvasOwned, op)
		1 * service.signupCodeService.create(recipient) >> invite
		1 * service.groovyPageRenderer.render(_) >> "<html>email</html>"
		1 * service.mailService.sendMail { _ }
	}

	void "save anonymous permission"() {
		setup:
		User me = new User(id: 1, username: "me@me.net").save(validate: false)
		Canvas canvasOwned = newCanvas("own")
		Resource res = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		Operation op = Operation.CANVAS_GET
		service.systemGrant(apiUser, canvasOwned, Operation.CANVAS_SHARE)
		when:
		service.saveAnonymousPermission(apiUser, op, res)
		then:
		service.check(apiUser, canvasOwned, op)
	}

	void "findAllPermissions() won't show list of permissions without 'share' permission (string id)"() {
		setup:
		Canvas canvas = new Canvas()
		canvas.save()
		Resource resource = new Resource(Canvas, canvas.id)
		User apiUser = me
		boolean subscriptions = false
		when:
		service.findAllPermissions(resource, apiUser, subscriptions)
		then:
		thrown NotPermittedException
	}

	void "findPermission finds permission"() {
		setup:
		Canvas canvasOwned = newCanvas("own")
		Resource resource = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		service.systemGrant(apiUser, canvasOwned, Operation.CANVAS_SHARE)
		Permission p = service.systemGrant(apiUser, canvasOwned, Operation.CANVAS_INTERACT)
		p.save(flush: true)
		when:
		Permission permission = service.findPermission(p.id, resource, apiUser)
		then:
		p == permission
	}

	void "findPermission throws NotFoundException when permission is not found "() {
		setup:
		Canvas canvasOwned = newCanvas("own")
		Resource resource = new Resource(Canvas, canvasOwned.id)
		User apiUser = me
		service.systemGrant(apiUser, canvasOwned, Operation.CANVAS_SHARE)
		Permission p = service.systemGrant(apiUser, canvasOwned, Operation.CANVAS_INTERACT)
		p.save(flush: true)
		when:
		Permission permission = service.findPermission(null, resource, apiUser)
		then:
		def e = thrown(NotFoundException)
		e.type == "Canvas"
		e.id == null
	}

	void "index won't show list of permissions without 'share' permission (Stream using id)"() {
		setup:
		Stream stream = new Stream()
		stream.id = "stream-id"
		stream.save()
		Resource resource = new Resource(Stream, stream.id)
		User apiUser = me
		boolean subscriptions = false
		when:
		service.findAllPermissions(resource, apiUser, subscriptions)
		then:
		1 * streamService.getStream(stream.id) >> stream
		thrown NotPermittedException
	}
}
