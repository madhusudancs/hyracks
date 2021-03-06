package edu.uci.ics.hyracks.storage.am.lsm.rtree.impls;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.io.IODeviceHandle;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexCursor;
import edu.uci.ics.hyracks.storage.am.common.api.IndexException;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMComponent;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIOOperation;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIOOperationCallback;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIndexAccessorInternal;

public class LSMRTreeMergeOperation implements ILSMIOOperation {
    private final ILSMIndexAccessorInternal accessor;
    private final List<ILSMComponent> mergingComponents;
    private final ITreeIndexCursor cursor;
    private final FileReference rtreeMergeTarget;
    private final FileReference btreeMergeTarget;
    private final FileReference bloomFilterMergeTarget;
    private final ILSMIOOperationCallback callback;

    public LSMRTreeMergeOperation(ILSMIndexAccessorInternal accessor, List<ILSMComponent> mergingComponents,
            ITreeIndexCursor cursor, FileReference rtreeMergeTarget, FileReference btreeMergeTarget,
            FileReference bloomFilterMergeTarget, ILSMIOOperationCallback callback) {
        this.accessor = accessor;
        this.mergingComponents = mergingComponents;
        this.cursor = cursor;
        this.rtreeMergeTarget = rtreeMergeTarget;
        this.btreeMergeTarget = btreeMergeTarget;
        this.bloomFilterMergeTarget = bloomFilterMergeTarget;
        this.callback = callback;
    }

    @Override
    public Set<IODeviceHandle> getReadDevices() {
        Set<IODeviceHandle> devs = new HashSet<IODeviceHandle>();
        for (ILSMComponent o : mergingComponents) {
            LSMRTreeImmutableComponent component = (LSMRTreeImmutableComponent) o;
            devs.add(component.getRTree().getFileReference().getDeviceHandle());
            if (component.getBTree() != null) {
                devs.add(component.getBTree().getFileReference().getDeviceHandle());
                devs.add(component.getBloomFilter().getFileReference().getDeviceHandle());
            }
        }
        return devs;
    }

    @Override
    public Set<IODeviceHandle> getWriteDevices() {
        Set<IODeviceHandle> devs = new HashSet<IODeviceHandle>();
        devs.add(rtreeMergeTarget.getDeviceHandle());
        if (btreeMergeTarget != null) {
            devs.add(btreeMergeTarget.getDeviceHandle());
            devs.add(bloomFilterMergeTarget.getDeviceHandle());
        }
        return devs;
    }

    @Override
    public void perform() throws HyracksDataException, IndexException {
        accessor.merge(this);
    }

    @Override
    public ILSMIOOperationCallback getCallback() {
        return callback;
    }

    public FileReference getRTreeMergeTarget() {
        return rtreeMergeTarget;
    }

    public FileReference getBTreeMergeTarget() {
        return btreeMergeTarget;
    }

    public FileReference getBloomFilterMergeTarget() {
        return bloomFilterMergeTarget;
    }

    public ITreeIndexCursor getCursor() {
        return cursor;
    }

    public List<ILSMComponent> getMergingComponents() {
        return mergingComponents;
    }
}
