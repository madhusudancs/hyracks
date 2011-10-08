package edu.uci.ics.hyracks.storage.am.btree.dataflow;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.common.api.IFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.dataflow.ITreeIndexOperatorDescriptorHelper;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IndexHelperOpenMode;
import edu.uci.ics.hyracks.storage.am.common.dataflow.TreeIndexOpHelper;
import edu.uci.ics.hyracks.storage.am.common.frames.LIFOMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.freepage.LinkedListFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;

public class BTreeOpHelper extends TreeIndexOpHelper {

    protected ITreeIndexOperatorDescriptorHelper opDesc;

    public BTreeOpHelper(ITreeIndexOperatorDescriptorHelper opDesc, IHyracksTaskContext ctx, int partition,
            IndexHelperOpenMode mode) {
        super(opDesc, ctx, partition, mode);
        this.opDesc = opDesc;
    }

    public ITreeIndex createTreeIndex() throws HyracksDataException {
        IBufferCache bufferCache = opDesc.getStorageManager().getBufferCache(ctx);
        ITreeIndexMetaDataFrameFactory metaDataFrameFactory = new LIFOMetaDataFrameFactory();
        IFreePageManager freePageManager = new LinkedListFreePageManager(bufferCache, indexFileId, 0,
                metaDataFrameFactory);
        return new BTree(bufferCache, opDesc.getTreeIndexFieldCount(), cmp, freePageManager, opDesc.getTreeIndexInteriorFactory(),
                opDesc.getTreeIndexLeafFactory());
    }

    public MultiComparator createMultiComparator(IBinaryComparator[] comparators) throws HyracksDataException {
        return new MultiComparator(opDesc.getTreeIndexTypeTraits(), comparators);
    }
}