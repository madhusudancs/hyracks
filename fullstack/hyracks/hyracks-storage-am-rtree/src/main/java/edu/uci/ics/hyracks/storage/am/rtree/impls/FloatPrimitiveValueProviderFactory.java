/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.storage.am.rtree.impls;

import edu.uci.ics.hyracks.dataflow.common.data.marshalling.FloatSerializerDeserializer;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProvider;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;

public class FloatPrimitiveValueProviderFactory implements
		IPrimitiveValueProviderFactory {
	private static final long serialVersionUID = 1L;

	public static final FloatPrimitiveValueProviderFactory INSTANCE = new FloatPrimitiveValueProviderFactory();

	private FloatPrimitiveValueProviderFactory() {
	}

	@Override
	public IPrimitiveValueProvider createPrimitiveValueProvider() {
		return new IPrimitiveValueProvider() {
			@Override
			public double getValue(byte[] bytes, int offset) {
				return FloatSerializerDeserializer.getFloat(bytes, offset);
			}
		};
	}
}