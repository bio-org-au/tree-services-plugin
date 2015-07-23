package au.org.biodiversity.nsl.tree

import org.apache.commons.logging.LogFactory
import spock.lang.*


import org.apache.commons.logging.Log

/**
 *
 */
class SequenceAndTimestampSpec extends Specification {
	static final Log log = LogFactory.getLog(SequenceAndTimestampSpec.class)


	// fields
	BasicOperationsService basicOperationsService

	def setup() {
	}

	def cleanup() {
	}

	def setupSpec() {
	}

	def cleanupSpec() {
	}

	// feature methods

	void "test get nextval"() {
		expect:
		basicOperationsService.getNextval() != 0
	}

	void "test get timestamp"() {
		expect:
		basicOperationsService.getTimestamp()
	}
}
