package frontlinesms2.message

import java.util.regex.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

import frontlinesms2.*
import frontlinesms2.enums.MessageStatus

class InboxSpec extends MessageGebSpec {
	def 'inbox message list is displayed'() {
		given:
			createInboxTestMessages()
		when:
			to MessagesPage
			def messageSources = $('#messages tbody tr td:nth-child(3)')*.text()
		then:
			messageSources == ['Alice', 'Bob']
		cleanup:
			deleteTestMessages()
	}
    
	def 'message details are shown in list'() {
		given:
			createInboxTestMessages()
		when:
			to MessagesPage
			def rowContents = $('#messages tbody tr:nth-child(1) td')*.text()
		then:
			rowContents[2] == 'Alice'
			rowContents[3] == 'hi Alice'
			rowContents[4] ==~ /[0-9]{2}-[A-Z][a-z]{2}-[0-9]{4} [0-9]{2}:[0-9]{2}/
		cleanup:
			deleteTestMessages()
	}
    
	def 'message to alice is first in the list, and links to the show page'() {
		given:
			createInboxTestMessages()
			def message = Fmessage.findBySrc('Alice')
		when:
			to MessagesPage
			def firstMessageLink = $('#messages tbody tr:nth-child(1) a', href:"/frontlinesms2/message/inbox/show/${message.id}")
		then:
			firstMessageLink.text() == 'Alice'
		cleanup:
			deleteTestMessages()
	}
        
	def 'selected message and its details are displayed'() {
		given:
			createInboxTestMessages()
			def message = Fmessage.findBySrc('Alice')
		when:
			go "message/inbox/show/${message.id}"
			def formatedDate = dateToString(message.dateCreated)
		then:
			$('#message-details #contact-name').text() == message.src
			$('#message-details #message-date').text() == formatedDate
			$('#message-details #message-body').text() == message.text
		cleanup:
			deleteTestMessages()
	}
    
	def 'selected message is highlighted'() {
		given:
			createInboxTestMessages()
			def aliceMessage = Fmessage.findBySrc('Alice')
			def bobMessage = Fmessage.findBySrc('Bob')
		when:
			go "message/inbox/show/${aliceMessage.id}"
		then:
			$('#messages .selected td:nth-child(3) a').getAttribute('href') == "/frontlinesms2/message/inbox/show/${aliceMessage.id}"
		when:
			go "message/inbox/show/${bobMessage.id}"
		then:
			$('#messages .selected td:nth-child(3) a').getAttribute('href') == "/frontlinesms2/message/inbox/show/${bobMessage.id}"
		cleanup:
			deleteTestMessages()
	}
	
	def 'CSS classes READ and UNREAD are set on corresponding messages'() {
		given:
			def m1 = new Fmessage(status:MessageStatus.INBOUND, read: false).save(failOnError:true)
			def m2 = new Fmessage(status:MessageStatus.INBOUND, read: true).save(failOnError:true)
			assert !m1.read
			assert m2.read
		when:
			go "message/inbox/show/$m2.id"
		then:
			$("tr#message-${m1.id}").hasClass('unread')
			!$("tr#message-${m1.id}").hasClass('read')
			
			!$("tr#message-${m2.id}").hasClass('unread')
			$("tr#message-${m2.id}").hasClass('read')
		cleanup:
			deleteTestMessages()
	}
	
	def 'contact name is displayed if message src is an existing contact'() {
		given:
			def message = new Fmessage(src:'+254778899', dst:'+254112233', text:'test', status:MessageStatus.INBOUND).save(failOnError:true)
			def contact = new Contact(name: 'June', primaryMobile: '+254778899').save(failOnError:true)
		when:
			to MessagesPage
			def rowContents = $('#messages tbody tr td:nth-child(3)')*.text()
		then:
			rowContents == ['June']
		cleanup:
			deleteTestMessages()
			deleteTestContacts()
	}

	def "should autopopulate the recipients name on click of reply for a inbox message"() {
		given:
			new Fmessage(src:'+254778899', dst:'+254112233', text:'test', status:MessageStatus.INBOUND).save(failOnError:true)
			new Contact(name: 'June', primaryMobile: '+254778899').save(failOnError:true)
		when:
			to MessagesPage
			println $('#btn_reply').text()
			$('#btn_reply').click()
			waitFor {$('div#tabs-1').displayed}
			$("div#tabs-1 .next").click()
		then:
			$('input', value:'+254778899').getAttribute('checked')
		cleanup:
			deleteTestMessages()
			deleteTestContacts()
	}

	def "should autopopulate the recipients name on click of reply even if the recipient is not in contact list"() {
		given:
			new Fmessage(src:'+254778899', dst:'+254112233', text:'test', status:MessageStatus.INBOUND).save(failOnError:true)
			new Contact(name: 'June', primaryMobile: '+254778899').save(failOnError:true)
			def message = new Fmessage(src:'+254999999', dst:'+254112233', text:'test', status:MessageStatus.INBOUND).save(failOnError:true)
		when:
			go "message/inbox/show/${message.id}"	
			$('#btn_reply').click()
			waitFor {$('div#tabs-1').displayed}
			$("div#tabs-1 .next").click()
		then:
			$('input', value:'+254999999').getAttribute('checked')
		cleanup:
			deleteTestMessages()
			deleteTestContacts()
	}

	def "should filter inbox messages for starred and unstarred messages"() {
		setup:
	    	createInboxTestMessages()
		when:
			go "message/inbox/show/${Fmessage.list()[0].id}"
		then:    	
			$("#messages tbody tr").size() == 2
		when:	
			$('a', text:'Starred').click()
			waitFor {$("#messages tbody tr").size() == 1}
		then:
			$("#messages tbody tr")[0].find("td:nth-child(3)").text() == 'Alice'
		when:
			$('a', text:'All').click()
			waitFor {$("#messages tbody tr").size() == 2}
		then:
			$("#messages tbody tr").collect {it.find("td:nth-child(3)").text()}.containsAll(['Alice', 'Bob'])
		cleanup:
			deleteTestMessages()
	}

	def "starred message filter should not be visible when there are no search results"() {
		when:
			go "message/inbox"
		then:
			$("#no-messages").text() == "No messages"
		    !$("a", text:"starred").displayed
	}

	def "should autopopulate the message body  when 'forward' is clicked"() {
		given:
			new Fmessage(src:'+254778899', dst:'+254112233', text:'test', status:MessageStatus.INBOUND).save(failOnError:true)
			def message = new Fmessage(src:'+254999999', dst:'+254112233', text:'test', status:MessageStatus.INBOUND).save(failOnError:true)
		when:
			go "message/inbox/show/${message.id}"
			$('#btn_dropdown').click()
			$('#btn_forward').click()			
			waitFor {$('div#tabs-1').displayed}
		then:
			$('textArea', name:'messageText').text() == "test"
		cleanup:
			deleteTestMessages()
	}
	
	def "should only display message details when one message is checked"() {
		given:
			createInboxTestMessages()
		when:
			to MessagesPage
			$("#message")[1].click()
			$("#message")[2].click()
		then:
			$('#checked-message-count').text() == "2 messages selected"
		when:
			$("#message")[1].click()
			def message = Fmessage.findBySrc('Bob')
			def formatedDate = dateToString(message.dateCreated)
		then:
			$("#message-details").displayed
			$('#message-details #contact-name').text() == message.src
			$('#message-details #message-date').text() == formatedDate
			$('#message-details #message-body').text() == message.text
		
		cleanup:
			deleteTestMessages()
	}

	def "should skip recipients tab if a message is replied"() {
		given:
			createInboxTestMessages()
		when:
			go "message"
		then:
			$("#btn_reply").click()
			waitFor {$('#tabs-1').displayed}
		when:
			$("#nextPage").jquery.trigger('click')
			waitFor { $('#tabs-3 ').displayed }
		then:
			$("#tabs-3").displayed
	}

	def "should skip recipients tab for reply-all option"() {
		given:
			createInboxTestMessages()
		when:
			go "message"
		then:
			$("#message")[0].click()
			$("a", text: "Reply All").click()
			waitFor {$('#tabs-1').displayed}
		when:
			$("#nextPage").jquery.trigger('click')
			waitFor { $('#tabs-3 ').displayed }
		then:
			$("#tabs-3").displayed
	}

	
//  NOTE: Need to find a better way to make this test work
//	def "should remain in the same page, after moving the message to the destination folder"() {
//		setup:
//			new Fmessage(text: "hello", status: MessageStatus.INBOUND).save(flush: true)
//			new Folder(name: "my-folder").save(flush: true)
//		when:
//			go "message/inbox"
//		then:
//			$('#message-actions').value("${Folder.findByName('my-folder').id}")
//			waitFor {$("#messages").text().contains("No messages")}
//			$("#messages-submenu .selected").text().contains('Inbox')
//		cleanup:
//			Fmessage.list()*.refresh()
//			Folder.findByName('my-folder').refresh().delete(flush: true)
//	}


	String dateToString(Date date) {
		DateFormat formatedDate = createDateFormat();
		return formatedDate.format(date)
	}

	DateFormat createDateFormat() {
		return new SimpleDateFormat("dd-MMM-yyyy hh:mm")
	}
}
