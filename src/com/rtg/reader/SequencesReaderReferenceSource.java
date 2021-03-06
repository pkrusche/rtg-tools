/*
 * Copyright (c) 2014. Real Time Genomics Limited.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.rtg.reader;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import com.rtg.mode.DnaUtils;
import com.rtg.util.diagnostic.Diagnostic;

import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.util.RuntimeIOException;

/**
 * Provides a <code>CRAMReferenceSource</code> backed by a SequencesReader.
 */
class SequencesReaderReferenceSource implements CRAMReferenceSource {

  private final SequencesReader mReader;
  private final Map<String, WeakReference<byte[]>> mCache = new HashMap<>();
  private Map<String, Long> mNames = null;

  /**
   * Construct a <code>CRAMReferenceSource</code> backed by an RTG SequencesReader
   * @param reader the backing SequencesReader
   */
  SequencesReaderReferenceSource(SequencesReader reader) {
    mReader = reader;
  }

  private synchronized Map<String, Long> getNames() throws IOException {
    if (mNames == null) {
      mNames = ReaderUtils.getSequenceNameMap(mReader.names());
    }
    return mNames;
  }

  private byte[] findInCache(final String name) {
    final WeakReference<byte[]> weakReference = mCache.get(name);
    if (weakReference != null) {
      final byte[] bytes = weakReference.get();
      if (bytes != null) {
        return bytes;
      }
    }
    return null;
  }

  @Override
  public synchronized byte[] getReferenceBases(final SAMSequenceRecord record, boolean tryVariants) {
    final String name = record.getSequenceName();
    final byte[] cached = findInCache(name);
    if (cached != null) {
      return cached;
    }

    Diagnostic.developerLog("CRAM get uncached reference bases for: " + name);
    try {
      final Long seqId = getNames().get(name);
      if (seqId == null) {
        return null;
      }

      final byte[] data = new byte[mReader.length(seqId)];
      mReader.read(seqId, data);
      // Convert from internal binary 0-4 encoding to the ASCII uppercase bases that htsjdk wants
      for (int i = 0; i < data.length; ++i) {
        data[i] = (byte) DnaUtils.getBase(data[i]);
      }
      mCache.put(record.getSequenceName(), new WeakReference<>(data));
      return data;
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe);
    }
  }
}
