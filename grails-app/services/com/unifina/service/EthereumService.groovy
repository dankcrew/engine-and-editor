package com.unifina.service

import com.unifina.domain.IntegrationKey
import com.unifina.domain.User
import com.unifina.signalpath.blockchain.Web3jHelper
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Utf8String
import org.web3j.protocol.Web3j

class EthereumService {
	private static final Logger log = LogManager.getLogger(EthereumService.class)

	String fetchJoinPartStreamID(String contractAddress) {
		Web3j web3j = Web3jHelper.getWeb3jConnectionFromConfig()
		try {
			return Web3jHelper.getPublicField(web3j, contractAddress, "joinPartStream", Utf8String.class)
		} catch (IOException e) {
			log.error("fetch data union joinPartStream id error", e)
			throw new RuntimeException(e)
		}
	}

	/**
	 * Calls owner() getter from data union contract
	 * @return admin's address, or null if contractAddress is faulty
	 */
	String fetchDataUnionAdminsEthereumAddress(String contractAddress) {
		Web3j web3j = Web3jHelper.getWeb3jConnectionFromConfig()
		try {
			return Web3jHelper.getPublicField(web3j, contractAddress, "owner", Address.class)
		} catch (IOException e) {
			log.error("fetch data union admins ethereum address error", e)
			throw new RuntimeException(e)
		}
	}

	/** Checks if given user has registered the given Ethereum address in their Streamr profile */
	boolean hasEthereumAddress(User user, String ethereumAddress) {
		IntegrationKey key = IntegrationKey.createCriteria().get {
			eq("user", user)
			eq("idInService", ethereumAddress, [ignoreCase: true])
			'in'("service", [IntegrationKey.Service.ETHEREUM, IntegrationKey.Service.ETHEREUM_ID])
		}
		return key != null
	}
}
