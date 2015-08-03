/*
    Copyright 2015 Australian National Botanic Gardens

    This file is part of NSL tree services plugin project.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy
    of the License at http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package au.org.biodiversity.nsl.tree;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SimpleProfiler {
	private final static Log			log				= LogFactory.getLog(SimpleProfiler.class);
	public static long					startloggingts;
	public static long					stoploggingts;
	public static Map<String, Long>		itemTime		= new HashMap<String, Long>();
	public static Map<String, Integer>	itemCt			= new HashMap<String, Integer>();
	private static boolean				profiling;

	private static Map<String, Long>	mostRecentStart	= new HashMap<String, Long>();

	public static void startProfiling() {
		startloggingts = System.currentTimeMillis();
		itemTime.clear();
		itemCt.clear();
		mostRecentStart.clear();
		profiling = true;
	}

	public static void stopProfiling() {
		profiling = false;

		stoploggingts = System.currentTimeMillis();
		log.info("logging for " + (stoploggingts-startloggingts) + " ms");
		
		
		Map<String,Long> byTime = new HashMap<String,Long>();
		
		for (String k :itemTime.keySet()) {
			long t = itemTime.get(k);
			int ct = itemCt.get(k);

			//byTime.put(k, (t / ct));
			byTime.put(k, t); // I mainly care about the total time
		}
		
		ArrayList<Entry<String, Long>> byTimeSorted = new ArrayList<Entry<String, Long>>( byTime.entrySet() );
		Collections.sort(byTimeSorted, new Comparator<Entry<String, Long>>(){
			public int compare(Entry<String, Long> o1, Entry<String, Long> o2) {
				return (int) (o1.getValue() - o2.getValue());
			}});
		

		for (Entry<String, Long> kk: byTimeSorted) {
			String k = kk.getKey();
			long t = itemTime.get(k);
			int ct = itemCt.get(k);

			log.info(k + " ct " + ct + " time " + t + " ms is " + (t * 100 / (stoploggingts - startloggingts))
					+ "% avg " + (t / ct));

		}
	}

	public static long totalTime() {
		return stoploggingts - startloggingts;
	}
	
	public static long totalMs(String k) {
		return itemTime.get(k);
	}
	
	public static long ct(String k) {
		return itemCt.get(k);
	}
	
	public static long avgMs(String k) {
		return totalMs(k) / ct(k);
	}
	
	public static double pct(String k) {
		return totalMs(k) * 100.0 / totalTime();
	}
	
	public static SortedSet<String> keys() {
		return new TreeSet<String>(itemCt.keySet());
	}
	
	public static void start(String s) {
		if (!profiling)
			return;
		mostRecentStart.put(s, System.currentTimeMillis());
	}

	public static void end(String s) {
		if (!profiling)
			return;
		if (!mostRecentStart.containsKey(s)) {
			throw new IllegalStateException(s);
		}
		itemCt.put(s, 1 + (itemCt.containsKey(s) ? itemCt.get(s) : 0));
		itemTime.put(s,
				(System.currentTimeMillis() - mostRecentStart.get(s))
						+ (itemTime.containsKey(s) ? itemTime.get(s) : 0L));
		mostRecentStart.remove(s);
	}
}
