package co.usc.net.messages;

import co.usc.blockchain.utils.BlockGenerator;
import co.usc.blockchain.utils.BlockGenerator;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 24/08/2017.
 */
public class BlockHeadersByHashMessageTest {
    @Test
    public void createMessage() {
        List<BlockHeader> blocks = new ArrayList<>();
        BlockGenerator blockGenerator = new BlockGenerator();

        Block block = blockGenerator.getGenesisBlock();

        for (int k = 1; k <= 4; k++) {
            Block b = blockGenerator.createChildBlock(block);
            blocks.add(b.getHeader());
        }

        BlockHeadersResponseMessage message = new BlockHeadersResponseMessage(1, blocks);

        Assert.assertEquals(1, message.getId());
        List<BlockHeader> mblocks = message.getBlockHeaders();

        Assert.assertEquals(mblocks.size(), blocks.size());

        for (int i = 0; i < blocks.size(); i++)
            Assert.assertEquals(blocks.get(1).getHash(), mblocks.get(1).getHash());
    }
}
