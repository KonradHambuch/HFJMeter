'use strict';

const fs = require('fs');
const { FileSystemWallet, X509WalletMixin } = require('fabric-network');
const path = require('path');

const assertExists = (dir) => {
    if (!fs.existsSync(dir)) {
        console.error(`Directory/file does not exist: ${dir}`);
        process.exit(1);
    }
};

const createIfNotExists = (dir) => {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, {recursive: true});
    }
};

const commandlineArgs = process.argv.slice(2);

if (commandlineArgs.length !== 4) {
    console.error('Proper usage: node convert.js <crypto-config path> <org name> <org MSP ID> <wallet dir>');
    process.exit(1);
}

const cryptoConfigDir = commandlineArgs[0];
const orgName = commandlineArgs[1];
const orgMspId = commandlineArgs[2];
const walletDir = commandlineArgs[3];

assertExists(cryptoConfigDir);

const orgUsersDir = path.join(cryptoConfigDir, 'peerOrganizations', orgName, 'users');
assertExists(orgUsersDir);
createIfNotExists(walletDir);

const wallet = new FileSystemWallet(walletDir);
const userDirNames = fs.readdirSync(orgUsersDir);

async function main() {
    try {
        for (const userDir of userDirNames) {
            const basePath = path.join(orgUsersDir, userDir);
            assertExists(basePath);

            const certPath = path.join(basePath, 'msp', 'signcerts', `${userDir}-cert.pem`);
            assertExists(certPath);
            
            const keyPath = path.join(basePath, 'msp', 'keystore', 'key.pem');
            assertExists(keyPath);

            const certPEM = fs.readFileSync(certPath).toString();
            const keyPEM = fs.readFileSync(keyPath).toString();

            const identity = X509WalletMixin.createIdentity(orgMspId, certPEM, keyPEM);
            if (await wallet.exists(userDir)) {
                console.log(`Deleting existing identity from wallet: ${userDir}`);
                await wallet.delete(userDir);
            }
            await wallet.import(userDir, identity);
        }

        console.log('Crypto artifacts converted to wallet:');
        console.log(await wallet.list())
    } catch (error) {
        console.log(`Error while converting crypto artifacts to wallet: ${error}`);
        console.log(error.stack);
    }
}

main().then(() => {
    process.exit(0);
}).catch((e) => {
    console.log(e);
    console.log(e.stack);
    process.exit(-1);
});