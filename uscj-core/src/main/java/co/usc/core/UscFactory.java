/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.usc.core;

import co.usc.config.UscSystemProperties;
import co.usc.core.bc.BlockChainImpl;
import co.usc.core.bc.TransactionPoolImpl;
import co.usc.metrics.HashRateCalculator;
import co.usc.mine.MinerClient;
import co.usc.mine.MinerServer;
import co.usc.net.*;
import co.usc.net.eth.UscWireProtocol;
import co.usc.net.handler.TxHandler;
import co.usc.net.handler.TxHandlerImpl;
import co.usc.net.sync.SyncConfiguration;
import co.usc.rpc.CorsConfiguration;
import co.usc.rpc.Web3UscImpl;
import co.usc.rpc.modules.eth.*;
import co.usc.rpc.modules.personal.PersonalModule;
import co.usc.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.usc.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.usc.rpc.modules.txpool.TxPoolModule;
import co.usc.rpc.netty.JsonRpcWeb3FilterHandler;
import co.usc.rpc.netty.JsonRpcWeb3ServerHandler;
import co.usc.rpc.netty.Web3HttpServer;
import co.usc.scoring.PeerScoringManager;
import co.usc.scoring.PunishmentParameters;
import co.usc.validators.ProofOfWorkRule;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.Repository;
import org.ethereum.core.genesis.BlockChainLoader;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.EthereumChannelInitializerFactory;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.eth.handler.EthHandlerFactory;
import org.ethereum.net.eth.handler.EthHandlerFactoryImpl;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.HandshakeHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.server.*;
import org.ethereum.rpc.Web3;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.sync.SyncPool;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.ethereum")
public class UscFactory {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Bean
    public PeerScoringManager getPeerScoringManager(SystemProperties config) {
        int nnodes = config.scoringNumberOfNodes();

        long nodePunishmentDuration = config.scoringNodesPunishmentDuration();
        int nodePunishmentIncrement = config.scoringNodesPunishmentIncrement();
        long nodePunhishmentMaximumDuration = config.scoringNodesPunishmentMaximumDuration();

        long addressPunishmentDuration = config.scoringAddressesPunishmentDuration();
        int addressPunishmentIncrement = config.scoringAddressesPunishmentIncrement();
        long addressPunishmentMaximunDuration = config.scoringAddressesPunishmentMaximumDuration();

        return new PeerScoringManager(nnodes, new PunishmentParameters(nodePunishmentDuration, nodePunishmentIncrement,
                nodePunhishmentMaximumDuration), new PunishmentParameters(addressPunishmentDuration, addressPunishmentIncrement, addressPunishmentMaximunDuration));
    }

    @Bean
    public NodeBlockProcessor getNodeBlockProcessor(Blockchain blockchain, BlockStore blockStore,
                                                    BlockNodeInformation blockNodeInformation, BlockSyncService blockSyncService, SyncConfiguration syncConfiguration) {
        return new NodeBlockProcessor(blockStore, blockchain, blockNodeInformation, blockSyncService, syncConfiguration);
    }

    @Bean
    public SyncProcessor getSyncProcessor(UscSystemProperties config,
                                          Blockchain blockchain,
                                          BlockSyncService blockSyncService,
                                          PeerScoringManager peerScoringManager,
                                          ChannelManager channelManager,
                                          SyncConfiguration syncConfiguration,
                                          DifficultyCalculator difficultyCalculator,
                                          ProofOfWorkRule proofOfWorkRule) {

        // TODO(lsebrie): add new BlockCompositeRule(new ProofOfWorkRule(), blockTimeStampValidationRule, new ValidGasUsedRule());
        return new SyncProcessor(config, blockchain, blockSyncService, peerScoringManager, channelManager,
                syncConfiguration, proofOfWorkRule, difficultyCalculator);
    }

    @Bean
    public BlockSyncService getBlockSyncService(UscSystemProperties config,
                                                Blockchain blockchain,
                                                BlockStore store,
                                                BlockNodeInformation nodeInformation,
                                                SyncConfiguration syncConfiguration) {
            return new BlockSyncService(config, store, blockchain, nodeInformation, syncConfiguration);
    }

    @Bean
    public SyncPool getSyncPool(@Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                Blockchain blockchain,
                                UscSystemProperties config,
                                NodeManager nodeManager) {
        return new SyncPool(ethereumListener, blockchain, config, nodeManager);
    }

    @Bean
    public TxHandler getTxHandler(UscSystemProperties config, CompositeEthereumListener compositeEthereumListener, Repository repository, Blockchain blockchain) {
        return new TxHandlerImpl(config, compositeEthereumListener, repository, blockchain);
    }

    @Bean
    public Web3 getWeb3(Usc usc,
                        Blockchain blockchain,
                        TransactionPool transactionPool,
                        UscSystemProperties config,
                        MinerClient minerClient,
                        MinerServer minerServer,
                        PersonalModule personalModule,
                        EthModule ethModule,
                        TxPoolModule txPoolModule,
                        ChannelManager channelManager,
                        Repository repository,
                        PeerScoringManager peerScoringManager,
                        NetworkStateExporter networkStateExporter,
                        org.ethereum.db.BlockStore blockStore,
                        ReceiptStore receiptStore,
                        PeerServer peerServer,
                        BlockProcessor nodeBlockProcessor,
                        HashRateCalculator hashRateCalculator,
                        ConfigCapabilities configCapabilities) {
        return new Web3UscImpl(
                usc,
                blockchain,
                transactionPool,
                config,
                minerClient,
                minerServer,
                personalModule,
                ethModule,
                txPoolModule,
                channelManager,
                repository,
                peerScoringManager,
                networkStateExporter,
                blockStore,
                receiptStore,
                peerServer,
                nodeBlockProcessor,
                hashRateCalculator,
                configCapabilities
        );
    }

    @Bean
    public JsonRpcWeb3FilterHandler getJsonRpcWeb3FilterHandler(UscSystemProperties uscSystemProperties) {
        return new JsonRpcWeb3FilterHandler(uscSystemProperties.corsDomains(), uscSystemProperties.rpcAddress(), uscSystemProperties.rpcHost());
    }

    @Bean
    public JsonRpcWeb3ServerHandler getJsonRpcWeb3ServerHandler(Web3 web3Service, UscSystemProperties uscSystemProperties) {
        return new JsonRpcWeb3ServerHandler(web3Service, uscSystemProperties.getRpcModules());
    }

    @Bean
    public Web3HttpServer getWeb3HttpServer(UscSystemProperties uscSystemProperties,
                                            JsonRpcWeb3FilterHandler filterHandler,
                                            JsonRpcWeb3ServerHandler serverHandler) {
        return new Web3HttpServer(
            uscSystemProperties.rpcAddress(),
            uscSystemProperties.rpcPort(),
            uscSystemProperties.soLingerTime(),
            true,
            new CorsConfiguration(uscSystemProperties.corsDomains()),
            filterHandler,
            serverHandler
        );
    }

    @Bean
    public BlockChainImpl getBlockchain(BlockChainLoader blockChainLoader) {
        return blockChainLoader.loadBlockchain();
    }

    @Bean
    public TransactionPool getTransactionPool(org.ethereum.db.BlockStore blockStore,
                                        ReceiptStore receiptStore,
                                        org.ethereum.core.Repository repository,
                                        UscSystemProperties config,
                                        ProgramInvokeFactory programInvokeFactory,
                                        @Qualifier("compositeEthereumListener") EthereumListener listener) {
        return new TransactionPoolImpl(
                blockStore,
                receiptStore,
                listener,
                programInvokeFactory,
                repository,
                config
        );
    }

    @Bean
    public SyncPool.PeerClientFactory getPeerClientFactory(SystemProperties config,
                                                           @Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                                           EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return () -> new PeerClient(config, ethereumListener, ethereumChannelInitializerFactory);
    }

    @Bean
    public EthereumChannelInitializerFactory getEthereumChannelInitializerFactory(ChannelManager channelManager, EthereumChannelInitializer.ChannelFactory channelFactory) {
        return remoteId -> new EthereumChannelInitializer(remoteId, channelManager, channelFactory);
    }

    @Bean
    public EthereumChannelInitializer.ChannelFactory getChannelFactory(UscSystemProperties config,
                                                                       @Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                                                       ConfigCapabilities configCapabilities,
                                                                       NodeManager nodeManager,
                                                                       EthHandlerFactory ethHandlerFactory,
                                                                       StaticMessages staticMessages,
                                                                       PeerScoringManager peerScoringManager) {
        return () -> {
            HandshakeHandler handshakeHandler = new HandshakeHandler(config, peerScoringManager);
            MessageQueue messageQueue = new MessageQueue();
            P2pHandler p2pHandler = new P2pHandler(config, ethereumListener, configCapabilities);
            MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
            return new Channel(config, messageQueue, p2pHandler, messageCodec, handshakeHandler, nodeManager, ethHandlerFactory, staticMessages);
        };
    }

    @Bean
    public EthHandlerFactoryImpl.UscWireProtocolFactory getRskWireProtocolFactory(PeerScoringManager peerScoringManager,
                                                                                  MessageHandler messageHandler,
                                                                                  Blockchain blockchain,
                                                                                  UscSystemProperties config,
                                                                                  CompositeEthereumListener ethereumListener){
        return () -> new UscWireProtocol(config, peerScoringManager, messageHandler, blockchain, ethereumListener);
    }

    @Bean
    public PeerServer getPeerServer(SystemProperties config,
                                    @Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                    EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return new PeerServerImpl(config, ethereumListener, ethereumChannelInitializerFactory);
    }

    @Bean
    public Wallet getWallet(UscSystemProperties config) {
        if (!config.isWalletEnabled()) {
            logger.info("Local wallet disabled");
            return null;
        }

        logger.info("Local wallet enabled");
        KeyValueDataSource ds = new LevelDbDataSource(config, "wallet");
        ds.init();
        return new Wallet(ds);
    }

    @Bean
    public PersonalModule getPersonalModuleWallet(UscSystemProperties config, Usc usc, Wallet wallet, TransactionPool transactionPool) {
        if (wallet == null) {
            return new PersonalModuleWalletDisabled();
        }

        return new PersonalModuleWalletEnabled(config, usc, wallet, transactionPool);
    }

    @Bean
    public EthModuleWallet getEthModuleWallet(UscSystemProperties config, Usc usc, Wallet wallet, TransactionPool transactionPool) {
        if (wallet == null) {
            return new EthModuleWalletDisabled();
        }

        return new EthModuleWalletEnabled(config, usc, wallet, transactionPool);
    }

    @Bean
    public EthModuleSolidity getEthModuleSolidity(UscSystemProperties config) {
        try {
            return new EthModuleSolidityEnabled(new SolidityCompiler(config));
        } catch (RuntimeException e) {
            // the only way we currently have to check if Solidity is available is catching this exception
            logger.debug("Solidity compiler unavailable", e);
            return new EthModuleSolidityDisabled();
        }
    }

    @Bean
    public SyncConfiguration getSyncConfiguration(UscSystemProperties config) {
        int expectedPeers = config.getExpectedPeers();
        int timeoutWaitingPeers = config.getTimeoutWaitingPeers();
        int timeoutWaitingRequest = config.getTimeoutWaitingRequest();
        int expirationTimePeerStatus = config.getExpirationTimePeerStatus();
        int maxSkeletonChunks = config.getMaxSkeletonChunks();
        int chunkSize = config.getChunkSize();
        return new SyncConfiguration(expectedPeers, timeoutWaitingPeers, timeoutWaitingRequest,
                expirationTimePeerStatus, maxSkeletonChunks, chunkSize);
    }

    @Bean
    public BlockStore getBlockStore(){
        return new BlockStore();
    }

    @Bean
    public TransactionGateway getTransactionGateway(
            ChannelManager channelManager,
            TransactionPool transactionPool,
            CompositeEthereumListener emitter){
        return new TransactionGateway(channelManager, transactionPool, emitter);
    }
}
