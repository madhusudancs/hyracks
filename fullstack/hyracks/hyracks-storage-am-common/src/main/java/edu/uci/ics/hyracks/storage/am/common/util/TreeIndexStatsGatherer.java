package edu.uci.ics.hyracks.storage.am.common.util;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.common.api.IFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrame;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrame;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.buffercache.ICachedPage;
import edu.uci.ics.hyracks.storage.common.file.BufferedFileHandle;

public class TreeIndexStatsGatherer {

	private final TreeIndexStats treeIndexStats = new TreeIndexStats();
	private final IBufferCache bufferCache;
	private final IFreePageManager freePageManager;
	private final int fileId;
	private final int rootPage;

	public TreeIndexStatsGatherer(IBufferCache bufferCache,
			IFreePageManager freePageManager, int fileId, int rootPage) {
		this.bufferCache = bufferCache;
		this.freePageManager = freePageManager;
		this.fileId = fileId;
		this.rootPage = rootPage;
	}

	public TreeIndexStats gatherStats(ITreeIndexFrame leafFrame,
			ITreeIndexFrame interiorFrame, ITreeIndexMetaDataFrame metaFrame)
			throws HyracksDataException {

		bufferCache.openFile(fileId);

		treeIndexStats.begin();

		int maxPageId = freePageManager.getMaxPage(metaFrame);
		for (int pageId = 0; pageId <= maxPageId; pageId++) {
			ICachedPage page = bufferCache.pin(
					BufferedFileHandle.getDiskPageId(fileId, pageId), false);
			page.acquireReadLatch();
			try {
				metaFrame.setPage(page);
				leafFrame.setPage(page);
				interiorFrame.setPage(page);

				if (leafFrame.isLeaf()) {
					if (pageId == rootPage) {
						treeIndexStats.addRoot(leafFrame);
					} else {
						treeIndexStats.add(leafFrame);
					}
				} else if (interiorFrame.isInterior()) {
					if (pageId == rootPage) {
						treeIndexStats.addRoot(interiorFrame);
					} else {
						treeIndexStats.add(interiorFrame);
					}
				} else {
					treeIndexStats.add(metaFrame, freePageManager);
				}

			} finally {
				page.releaseReadLatch();
				bufferCache.unpin(page);
			}
		}

		treeIndexStats.end();

		bufferCache.closeFile(fileId);

		return treeIndexStats;
	}
}
