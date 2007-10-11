/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2007 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.server;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import tigase.annotations.TODO;
import tigase.conf.Configurable;
import tigase.stats.StatRecord;
import tigase.stats.StatisticType;
import tigase.stats.StatisticsContainer;
import tigase.util.JIDUtils;
import tigase.util.DNSResolver;

/**
 * Describe class AbstractMessageReceiver here.
 *
 *
 * Created: Tue Nov 22 07:07:11 2005
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public abstract class AbstractMessageReceiver
  implements StatisticsContainer, MessageReceiver, Configurable {

	protected static final long SECOND = 1000;
	protected static final long MINUTE = 60*SECOND;
	protected static final long HOUR = 60*MINUTE;

	private String DEF_HOSTNAME_PROP_VAL = "localhost";
	public static final String MAX_QUEUE_SIZE_PROP_KEY = "max-queue-size";
	//  public static final Integer MAX_QUEUE_SIZE_PROP_VAL = Integer.MAX_VALUE;
  public static final Integer MAX_QUEUE_SIZE_PROP_VAL = 1000;

  /**
   * Variable <code>log</code> is a class logger.
   */
  private static final Logger log =
    Logger.getLogger("tigase.server.AbstractMessageReceiver");

  private int maxQueueSize = MAX_QUEUE_SIZE_PROP_VAL;
	private String defHostname = DEF_HOSTNAME_PROP_VAL;

  private MessageReceiver parent = null;
	//	private Timer delayedTask = new Timer("MessageReceiverTask", true);

  private LinkedBlockingQueue<QueueElement> in_queue =
		new LinkedBlockingQueue<QueueElement>(maxQueueSize);
  private LinkedBlockingQueue<QueueElement> out_queue =
		new LinkedBlockingQueue<QueueElement>(maxQueueSize);

// 	private String sync = "SyncObject";

	private Timer receiverTasks = null;
	private Thread in_thread = null;
	private Thread out_thread = null;
  private boolean stopped = false;
  private String name = null;
	private Set<String> routings = new CopyOnWriteArraySet<String>();
	private Set<Pattern> regexRoutings = new CopyOnWriteArraySet<Pattern>();
	private int curr_second = 0;
	private int[] seconds = new int[60];
	private int sec_idx = 0;
	private int[] minutes = new int[60];
	private int min_idx = 0;

  /**
   * Variable <code>statAddedMessagesOk</code> keeps counter of successfuly
   * added messages to queue.
   */
  private long statAddedMessagesOk = 0;
  /**
   * Variable <code>statAddedMessagesEr</code> keeps counter of unsuccessfuly
   * added messages due to queue overflow.
   */
  private long statAddedMessagesEr = 0;

	/**
	 * Describe <code>myDomain</code> method here.
	 *
	 * @return a <code>String</code> value
	 */
	public String myDomain() {
		return getName() + "." + defHostname;
	}

  /**
   * Describe <code>addMessage</code> method here.
   *
   * @param packet a <code>Packet</code> value
   */
  public boolean addPacket(Packet packet) {
    return prAddPacket(packet);
  }

  public boolean addPackets(Queue<Packet> packets) {
		Packet p = null;
		boolean result = true;
		while ((p = packets.peek()) != null) {
			result = prAddPacket(p);
			if (result) {
				packets.poll();
			} else {
				return false;
			} // end of if (result) else
		} // end of while ()
    return true;
  }

	private boolean prAddPacket(Packet packet) {
		++curr_second;
		try {
// 			log.finest(">" + getName() + "<  " +
// 				"Adding packet to inQueue: " + packet.getStringData());
			in_queue.put(new QueueElement(QueueElementType.IN_QUEUE, packet));
			++statAddedMessagesOk;
		} catch (InterruptedException e) {
			++statAddedMessagesEr;
			return false;
		} // end of try-catch
		return true;
  }

	protected boolean addOutPacket(Packet packet) {
		++curr_second;
		try {
// 			log.finest(">" + getName() + "<  " +
// 				"Adding packet to outQueue: " + packet.getStringData());
			out_queue.put(new QueueElement(QueueElementType.OUT_QUEUE, packet));
			++statAddedMessagesOk;
		} catch (InterruptedException e) {
			++statAddedMessagesEr;
			return false;
		} // end of try-catch
		return true;
	}

	/**
	 * Non blocking version of <code>addOutPacket</code>.
	 *
	 * @param packet a <code>Packet</code> value
	 * @return a <code>boolean</code> value
	 */
	protected boolean addOutPacketNB(Packet packet) {
		++curr_second;
		log.finest(">" + getName() + "<  " +
			"Adding packet to outQueue: " + packet.getStringData());
		boolean result =
			out_queue.offer(new QueueElement(QueueElementType.OUT_QUEUE, packet));
		if (result) {
			++statAddedMessagesOk;
		} else {
			++statAddedMessagesEr;
		}
		return result;
	}

	protected boolean addOutPackets(Queue<Packet> packets) {
		Packet p = null;
		boolean result = true;
		while ((p = packets.peek()) != null) {
			result = addOutPacket(p);
			if (result) {
				packets.poll();
			} else {
				return false;
			} // end of if (result) else
		} // end of while ()
    return true;
	}

  public abstract void processPacket(Packet packet);

  public List<StatRecord> getStatistics() {
    List<StatRecord> stats = new LinkedList<StatRecord>();
    stats.add(new StatRecord(getName(), StatisticType.QUEUE_SIZE,
				(in_queue.size() + out_queue.size()), Level.FINEST));
    stats.add(new StatRecord(getName(), StatisticType.MSG_RECEIVED_OK,
				statAddedMessagesOk, Level.FINE));
    stats.add(new StatRecord(getName(), StatisticType.QUEUE_OVERFLOW,
				statAddedMessagesEr, Level.FINEST));
		stats.add(new StatRecord(getName(), "Last second packets", "int",
				seconds[(sec_idx == 0 ? 59 : sec_idx - 1)], Level.FINE));
		stats.add(new StatRecord(getName(), "Last minute packets", "int",
				minutes[(min_idx == 0 ? 59 : min_idx - 1)], Level.FINE));
		int curr_hour = 0;
		for (int min: minutes) { curr_hour += min; }
		stats.add(new StatRecord(getName(), "Last hour packets", "int",
				curr_hour, Level.FINE));
    return stats;
  }

  /**
   * Sets all configuration properties for object.
   */
  public void setProperties(Map<String, Object> properties) {
    int queueSize = (Integer)properties.get(MAX_QUEUE_SIZE_PROP_KEY);
    setMaxQueueSize(queueSize);
		defHostname = (String)properties.get(DEF_HOSTNAME_PROP_KEY);
		addRouting(myDomain());
  }

  public void setMaxQueueSize(int maxQueueSize) {
    if (this.maxQueueSize != maxQueueSize) {
			stopThreads();
      this.maxQueueSize = maxQueueSize;
      if (in_queue != null) {
				LinkedBlockingQueue<QueueElement> newQueue =
					new LinkedBlockingQueue<QueueElement>(maxQueueSize);
				newQueue.addAll(in_queue);
				in_queue = newQueue;
      } // end of if (queue != null)
      if (out_queue != null) {
				LinkedBlockingQueue<QueueElement> newQueue =
					new LinkedBlockingQueue<QueueElement>(maxQueueSize);
				newQueue.addAll(out_queue);
				out_queue = newQueue;
      } // end of if (queue != null)
			startThreads();
    } // end of if (this.maxQueueSize != maxQueueSize)
  }

//   public void setLocalAddresses(String[] addresses) {
//     localAddresses = addresses;
//   }

	protected Integer getDefMaxQueueSize() {
		return MAX_QUEUE_SIZE_PROP_VAL;
	}

  /**
   * Returns defualt configuration settings for this object.
   */
  public Map<String, Object> getDefaults(Map<String, Object> params) {
    Map<String, Object> defs = new LinkedHashMap<String, Object>();
		defs.put(MAX_QUEUE_SIZE_PROP_KEY, getDefMaxQueueSize());
		if (params.get(GEN_VIRT_HOSTS) != null) {
			DEF_HOSTNAME_PROP_VAL = ((String)params.get(GEN_VIRT_HOSTS)).split(",")[0];
		} else {
			DEF_HOSTNAME_PROP_VAL = DNSResolver.getDefHostNames()[0];
		}
		defs.put(DEF_HOSTNAME_PROP_KEY, DEF_HOSTNAME_PROP_VAL);

    return defs;
  }

  public void release() {
    stop();
  }

  public void setParent(MessageReceiver parent) {
    this.parent = parent;
		//addRouting(getDefHostName());
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

	private void stopThreads() {
    stopped = true;
		try {
			if (in_thread != null) {
				in_thread.interrupt();
				while (in_thread.isAlive()) {
					Thread.sleep(100);
				}
			}
			if (out_thread != null) {
				out_thread.interrupt();
				while (out_thread.isAlive()) {
					Thread.sleep(100);
				}
			}
		} catch (InterruptedException e) {}
		in_thread = null;
		out_thread = null;
		receiverTasks.cancel();
		receiverTasks = null;
	}

	private void startThreads() {
		if (in_thread == null || ! in_thread.isAlive()) {
			stopped = false;
			in_thread = new Thread(new QueueListener(in_queue));
			in_thread.setName("in_" + name);
			in_thread.start();
		} // end of if (thread == null || ! thread.isAlive())
		if (out_thread == null || ! out_thread.isAlive()) {
			stopped = false;
			out_thread = new Thread(new QueueListener(out_queue));
			out_thread.setName("out_" + name);
			out_thread.start();
		} // end of if (thread == null || ! thread.isAlive())
		receiverTasks = new Timer(getName() + " tasks", true);
		receiverTasks.schedule(new TimerTask() {
				public void run() {
					seconds[sec_idx++] = curr_second;
					curr_second = 0;
					if (sec_idx >= 60) {
						sec_idx = 0;
					}
				}
			}, 10*SECOND, SECOND);
		receiverTasks.schedule(new TimerTask() {
				public void run() {
					int curr_minute = 0;
					for (int sec: seconds) {
						curr_minute += sec;
					}
					minutes[min_idx++] = curr_minute;
					if (min_idx >= 60) {
						min_idx = 0;
					}
				}
			}, 10*SECOND+MINUTE, MINUTE);

	}

	public void start() {
		log.finer(getName() + ": starting queue management threads ...");
		startThreads();
  }

  public void stop() {
		log.finer(getName() + ": stopping queue management threads ...");
		stopThreads();
  }

	public String getDefHostName() {
		return defHostname;
	}

	public Set<String> getRoutings() {
		return routings;
	}

	public Set<Pattern> getRegexRoutings() {
		return regexRoutings;
	}

	public void addRouting(String address) {
		routings.add(address);
		log.fine(getName() + " - added routing: " + address);
	}

	public boolean removeRouting(String address) {
		return routings.remove(address);
	}

	public void clearRoutings() {
		routings.clear();
	}

	public boolean isInRoutings(String host) {
		return routings.contains(host);
	}

	public void addRegexRouting(String address) {
		regexRoutings.add(Pattern.compile(address, Pattern.CASE_INSENSITIVE));
	}

	public boolean removeRegexRouting(String address) {
		return regexRoutings.remove(Pattern.compile(address,
				Pattern.CASE_INSENSITIVE));
	}

	public void clearRegexRoutings() {
		regexRoutings.clear();
	}

	public boolean isInRegexRoutings(String address) {
// 		log.finest(getName() + " looking for regex routings: " + address);
		for (Pattern pat: regexRoutings) {
			if (pat.matcher(address).matches()) {
				log.finest(getName() + " matched against pattern: " + pat.toString());
				return true;
			}
// 			log.finest(getName() + " matching failed against pattern: " + pat.toString());
		}
		return false;
	}

	public void processPacket(final Packet packet, final Queue<Packet> results)	{
		// do nothing, this method is called directly by MessageRouter
		// and should not be used normally by the component.
	}

	private enum QueueElementType { IN_QUEUE, OUT_QUEUE }

	private static class QueueElement {
		private QueueElementType type = null;
		private Packet packet = null;

		private QueueElement(QueueElementType type, Packet packet) {
			this.type = type;
			this.packet = packet;
		}

	}

	private class QueueListener implements Runnable {

		private LinkedBlockingQueue<QueueElement> queue = null;

		private QueueListener(LinkedBlockingQueue<QueueElement> q) {
			this.queue = q;
		}

		public void run() {
			while (! stopped) {
				try {
					QueueElement qel = queue.take();
					switch (qel.type) {
					case IN_QUEUE:
						processPacket(qel.packet);
						break;
					case OUT_QUEUE:
						if (parent != null) {
// 							log.finest(">" + getName() + "<  " +
// 								"Sending outQueue to parent: " + parent.getName());
							parent.addPacket(qel.packet);
						} else {
							// It may happen for MessageRouter and this is intentional
							prAddPacket(qel.packet);
							//log.warning(">" + getName() + "<  " + "No parent!");
						} // end of else
						break;
					default:
						log.severe("Unknown queue element type: " + qel.type);
						break;
					} // end of switch (qel.type)
				} catch (InterruptedException e) {
					//log.log(Level.SEVERE, "Exception during packet processing: ", e);
					//				stopped = true;
				} catch (Exception e) {
					log.log(Level.SEVERE, "Exception during packet processing: ", e);
				} // end of try-catch
			} // end of while (! stopped)
		}

	}

} // AbstractMessageReceiver
