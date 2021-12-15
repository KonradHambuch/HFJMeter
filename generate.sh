#!/usr/bin/env bash

# if the artifacts already exist, do nothing
if [[ -d "crypto-config" ]]; then
  echo "Crypto artifacts already exist, exiting"
  exit 0
fi

rm -f ./tls/*.block
rm -f ./tls/*.tx
rm -f ./tls/*.json
rm -f ./notls/*.block
rm -f ./notls/*.tx
rm -f ./notls/*.json

# Generate crypto artifacts
cryptogen generate --config=./crypto-config.yaml

# Rename the key files we use to be "key.pem" instead of a UUID
for KEY in $(find crypto-config -type f -name "*_sk"); do
    KEY_DIR=$(dirname ${KEY})
    mv ${KEY} ${KEY_DIR}/key.pem
done

CHANNEL=epengo-channel

# $1: tls/notls
function generate_artifacts() {
  # Generate genesis block and JSON
  configtxgen -configPath ./${1} -profile OrdererGenesis -outputBlock ./${1}/genesis.block -channelID syschannel
  configtxlator proto_decode --type=common.Block --input=./${1}/genesis.block > ./${1}/genesis.json

  # Generate channel block and JSON
  configtxgen -configPath ./${1} -profile ChannelConfig -outputCreateChannelTx ./${1}/${CHANNEL}.tx -channelID ${CHANNEL}
  configtxlator proto_decode --type=common.Envelope --input=./${1}/${CHANNEL}.tx > ./${1}/${CHANNEL}.json

  # Generate anchor peer updates and JSONs
  configtxgen -configPath ./${1} -profile ChannelConfig -outputAnchorPeersUpdate ./${1}/${CHANNEL}-cb-anchor.tx -channelID ${CHANNEL} -asOrg CentralBankOrg
  configtxlator proto_decode --type=common.Envelope --input=./${1}/${CHANNEL}-cb-anchor.tx > ./${1}/${CHANNEL}-cb-anchor.json

  configtxgen -configPath ./${1} -profile ChannelConfig -outputAnchorPeersUpdate ./${1}/${CHANNEL}-fi-anchor.tx -channelID ${CHANNEL} -asOrg FIOrg
  configtxlator proto_decode --type=common.Envelope --input=./${1}/${CHANNEL}-fi-anchor.tx > ./${1}/${CHANNEL}-fi-anchor.json
}

# configtxgen needs this...
export FABRIC_CFG_PATH=$PWD

generate_artifacts "tls"
generate_artifacts "notls"