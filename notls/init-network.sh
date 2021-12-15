#!/bin/bash

export NUM_OF_ORGS=2
export NUM_OF_PEERS_MINUS_ONE=1
export CHANNEL_NAME=epengo-channel
export CONFIG_ROOT=/etc/fabric-config
export CRYPTO=${CONFIG_ROOT}/crypto-config
export GODEBUG=netdns=go
export SLEEP_TIME=5

export FABRIC_CFG_PATH=/etc/hyperledger/fabric
export ORDERER_URL=orderer0.example.com:7050
export ORDERER_CAFILE=${CRYPTO}/ordererOrganizations/example.com/orderers/orderer0.example.com/tls/ca.crt
export CORE_PEER_TLS_ENABLED=false

function log() {
    echo "#### ${1}"
}

# $1: org MSP ID
# $2: org name
function setup_org_peer_context() {
    export CORE_PEER_LOCALMSPID=${1}
    export CORE_PEER_MSPCONFIGPATH=${CRYPTO}/peerOrganizations/${2}.example.com/users/Admin@${2}.example.com/msp
    export CORE_PEER_ADDRESS=peer0.${2}.example.com:7051
}

function use_central_bank() {
    log "Switching to central bank identity"
    setup_org_peer_context "CentralBankOrgMSP" "centralbank"
}

function use_fi() {
    log "Switching to FI identity"
    setup_org_peer_context "FIOrgMSP" "fi"
}

function safety_sleep() {
    log "Sleeping ${SLEEP_TIME}s"
    sleep ${SLEEP_TIME}s
}

function join_channel() {
    # The "peer channel create" command will create a local file "<channel_name>.block"
    log "Joining channel"
    if peer channel join --blockpath ${CHANNEL_NAME}.block
    then
        log "Joined channel"
    else
        log "Failed to join channel: $?"
        exit 1
    fi
}

# $1: "cb" or "fi"
function update_anchor() {
    log "Updating anchor peer"
    FILE=${CONFIG_ROOT}/notls/${CHANNEL_NAME}-$1-anchor.tx
    if peer channel update --orderer ${ORDERER_URL} --channelID ${CHANNEL_NAME} --file ${FILE}
    then
        log "Anchor peer updated"
    else
        log "Anchor peer update failed: $?"
        exit 1
    fi
}

# $1: name
# $2: version
# $3: path
# $4: language
function install_chaincode() {
    log "Installing chaincode: $1"
    if peer chaincode install --name "$1" --version "$2" --path "$3" --lang $4
    then
        log "Chaincode installed"
    else
        log "Chaincode install failed: $?"
        exit 1
    fi
}

# $1: name
# $2: version
function instantiate_chaincode() {
    log "Instantiating chaincode: $1"
    POLICY="AND('CentralBankOrgMSP.member','FIOrgMSP.member')"
    if peer chaincode instantiate --orderer ${ORDERER_URL} --channelID ${CHANNEL_NAME} --name $1 --version $2 --policy ${POLICY} --ctor '{"Args":[]}'
    then
        log "Chaincode instantiated"
    else
        log "Chaincode instantiation failed: $?"
        exit 1
    fi
}

# $1: name
# $2: org domain
function ping_chaincode() {
    log "Pinging chaincode $1 on peer0.$2"
    if peer chaincode query --channelID ${CHANNEL_NAME} --name $1 --ctor '{"Args":["ping"]}' --peerAddresses "peer0.${2}:7051"
    then
        log "Chaincode pinged"
    else
        log "Chaincode ping failed: $?"
        exit 1
    fi
}



function install_cbdc_chaincode() {
    install_chaincode "cbdc" "1.0.0" "/opt/contracts/cbdc" "node"
}

function install_fabcar_chaincode() {
    install_chaincode "fabcar" "1.0.0" "/opt/contracts/fabcar" "node"
}

use_central_bank

log "Creating channel"
if peer channel create --orderer ${ORDERER_URL} --channelID ${CHANNEL_NAME} --file ${CONFIG_ROOT}/notls/${CHANNEL_NAME}.tx
then
    log "Channel created"
else
    log "Channel creation failed: $?"
    exit 1
fi

safety_sleep

use_central_bank
join_channel

use_fi
join_channel

# safety_sleep

use_central_bank
update_anchor "cb"

use_fi
update_anchor "fi"

# safety_sleep

use_central_bank
install_cbdc_chaincode

use_fi
install_cbdc_chaincode

use_central_bank
instantiate_chaincode "cbdc" "1.0.0"

safety_sleep

ping_chaincode "cbdc" "centralbank.example.com"
ping_chaincode "cbdc" "fi.example.com"