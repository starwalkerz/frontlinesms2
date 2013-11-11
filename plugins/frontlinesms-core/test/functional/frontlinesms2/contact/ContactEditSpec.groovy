package frontlinesms2.contact

import frontlinesms2.*

import geb.Browser
import grails.plugin.geb.GebSpec
import frontlinesms2.message.*

class ContactEditSpec extends ContactBaseSpec {
	def setup() {
		createTestContacts()
	}

	def 'selected contact details can be edited and saved, which updates contact list values'() {
		given:
			def aliceId = remote { Contact.findByName('Alice').id }
		when:
			to PageContactShow, aliceId

			singleContactDetails.name.value('Kate')
			header.click()

			sleep 4000
			singleContactDetails.mobile.value('+2541234567')
			header.click()

			sleep 4000
			singleContactDetails.email.value('gaga@gmail.com')
			header.click()
		then:
			assertFieldDetailsCorrect('name', 'Name', 'Kate')
			assertFieldDetailsCorrect('mobile', 'Mobile', '+2541234567')
			remote { Contact.findById(aliceId).name } == 'Kate'
			waitFor {
				contactList.selectedContact.text().contains('Kate')
				contactList.selectedContact.text().contains('+2541234567')
			}
	}

	def "should disable the save and cancel buttons when viewing a contact details"() {
		when:
			to PageContactShow, remote { Contact.findByName('Bob').id }
		then:
			singleContactDetails.save.disabled
	}

	def "should remain on the same page after updating a contact"() {
		given:
			createManyContacts()
		when:
			to PageContactShow, remote { Contact.findByName('Bob').id }
			footer.nextPage.click()
		then:
			!footer.prevPage.disabled
		when:
			singleContactDetails.name = 'Kate'
			singleContactDetails.mobile.click()
		then:
			!footer.prevPage.disabled
	}

	def "should display a count of messages recieved and sent for a contact"() {
		given: 'A contact has received and sent messages'
			remote {
				def sent1 = new Fmessage(inbound:false, text:"outbound 1")
				def sent2 = new Fmessage(inbound:false, text:"outbound 2")
				sent1.addToDispatches(dst:'2541234567', status:DispatchStatus.SENT, dateSent:new Date()).save(failOnError:true, flush:true)
				sent2.addToDispatches(dst:'2541234567', status:DispatchStatus.SENT, dateSent:new Date()).save(failOnError:true, flush:true)
				new Fmessage(src:'2541234567', text:"inbound 1", date: new Date(), inbound:true).save(failOnError:true, flush:true)
				null
			}
		when:
			to PageContactShow, remote { Contact.findByName('Alice').id }
		then:
			singleContactDetails.sentCount == 'contact.messages.sent[2]'
			singleContactDetails.receivedCount == 'contact.received.messages[1]'
	}

	def 'contact fields in the list are not updated if save was unsuccessful'() {
		given:
			def aliceId = remote { Contact.findByName('Alice').id }
		when:
			to PageContactShow, aliceId

			singleContactDetails.name.value('')
			header.click()

		then:
			waitFor {
				!contactList.selectedContact.text().contains('Kate')
			}
		when:
			singleContactDetails.mobile.value('')
			header.click()

			sleep 4000
		then:
			remote { Contact.findById(aliceId).name } == ''
			remote { Contact.findById(aliceId).mobile } == '2541234567'
			waitFor {
				contactList.selectedContact.text().contains('2541234567')
			}
	}
}

