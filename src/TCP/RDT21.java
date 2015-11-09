package TCP;
import java.io.IOException;

/**
 * Implements simulator using rdt2.1 protocol
 * 
 * @author rms
 *
 */
public class RDT21 extends RTDBase {

    /**
     * Constructs an RDT21 simulator with given munge factor
     * @param pmunge		probability of character errors
     * @throws IOException	if channel transmissions fail
     */
    public RDT21(double pmunge) throws IOException {this(pmunge, 0.0, null);}

    /**
     * Constructs an RDT21 simulator with given munge factor, loss factor and file feed
     * @param pmunge		probability of character errors
     * @param plost			probability of packet loss
     * @param filename		file used for automatic data feed
     * @throws IOException	if channel transmissions fail
     */
    public RDT21(double pmunge, double plost, String filename) throws IOException {
        super(pmunge, plost, filename);
        sender = new RSender21();
        receiver = new RReceiver21();
    }

    /**
     * Packet appropriate for rdt2.1;
     * contains data, seqnum and checksum
     * @author rms
     *
     */
    public static class Packet implements PacketType {
        String checksum;
        String data;
        String seqnum;
        /**
         * Constructs a packet out of data with empty seqnum and computed checksum 
         * @param data	content of this packet
         */
        public Packet(String data){
            this(data, " ");
        }
        /**
         * Constructs a packet out of data and seqnum with computed checksum
         * @param data	content of this packet
         * @param seqnum	sequence number assigned to this packet
         */
        public Packet(String data, String seqnum){
            this(data, seqnum, CkSum.genCheck(seqnum+data));
        }
        /**
         * Constructs a packet out of data and seqnum with assigned checksum
         * @param data	content of this packet
         * @param seqnum	sequence number assigned to this packet
         * @param checksum	assigned checksum	
         */
        public Packet(String data, String seqnum, String checksum) {
            this.data = data;
            this.seqnum = seqnum;
            this.checksum = checksum;
        }
        /**
         * Static method to create a packet from serialized data 
         * @param data	serialized version of a packet created by the serialize method 
         * @return	packet constructed from data
         */
        public static Packet deserialize(String data) {
            String hex = data.substring(0, 4);
            String seqnum = data.substring(4,5);
            String dat = data.substring(5);
            return new Packet(dat, seqnum, hex);
        }
        public boolean isAck() {
            return data.equals("ACK");
        }
        @Override
            public String serialize() {
                return checksum+seqnum+data;
            }
        @Override
            public boolean isCorrupt() {
                return !CkSum.checkString(seqnum+data, checksum);
            }
        @Override
            /**
             * For printing in output log
             */
            public String toString() {
                return String.format("%s %s (%s/%s)", data, seqnum, checksum, CkSum.genCheck(seqnum+data));
            }
    }
    /**
     * RSender Class implementing rdt2.1 protocol
     * @author rms
     *
     */
    public class RSender21 extends RSender {
        Packet packet = null;
        @Override
            public int loop(int myState) throws IOException {
                Packet pack = null;
                String acknak = null;
                String dat = null;
                switch(myState) {
                    case 0:
                        dat = getFromApp(0);
                        packet = new Packet(dat, "0");
                        System.out.printf("Sender(0): %s\n", packet.toString());
                        System.out.println("  **Sender(0->1):");
                        forward.send(packet);
                        return 1;
                    case 1:
                        acknak = backward.receive();
                        pack = Packet.deserialize(acknak);
                        System.out.printf("  **Sender(1): %s ***\n", pack.toString());
                        if (!pack.isAck() || pack.isCorrupt()) {
                            System.out.println("  **Sender(1->1): NAK or corrupt acknowledgement; resending ***");
                            forward.send(packet);
                            return 1;
                        }
                        System.out.println("  **Sender(1->2)");
                        return 2;
                    case 2:
                        dat = getFromApp(0);
                        packet = new Packet(dat, "1");
                        System.out.printf("Sender(2): %s\n", packet.toString());
                        System.out.println("  **Sender(2->3):");
                        forward.send(packet);
                        return 3;
                    case 3:
                        acknak = backward.receive();
                        pack = Packet.deserialize(acknak);
                        System.out.printf("  **Sender(3): %s ***\n", pack.toString());
                        if (!pack.isAck() || pack.isCorrupt()) {
                            System.out.println("  **Sender(3->3): NAK or corrupt acknowledgement; resending ***");
                            forward.send(packet);
                            return 3;
                        }
                        System.out.println("  **Sender(3->0)");
                        return 0;
                }
                return myState;
            }
    }

    /**
     * RReceiver Class implementing rdt2.1 protocol
     * @author rms
     *
     */
    public class RReceiver21 extends RReceiver {
        @Override
            public int loop(int myState) throws IOException {
                String dat = null;
                Packet packet = null;
                Packet p = null;
                switch (myState) {
                    case 0:
                        dat = forward.receive();
                        packet = Packet.deserialize(dat);
                        System.out.printf("         **Receiver(0): %s ***\n", packet.toString());
                        if (packet.isCorrupt()){
                        	System.out.println("         **Receiver(0->0): corrupt data; replying NAK **");
                        	p = new Packet("NAK", "0");
                        	backward.send(p);
                            return 0;
                        }
                        System.out.println("         **Receiver(0->1): ok data; replying ACK **");
                        deliverToApp(packet.data);
                        p = new Packet("ACK", "0");
                        backward.send(p);
                        return 1;
                    case 1:
                        dat = forward.receive();
                        packet = Packet.deserialize(dat);
                        System.out.printf("         **Receiver(1): %s ***\n", packet.toString());
                        if (packet.isCorrupt()){
                        	System.out.println("         **Receiver(1->1): corrupt data; replying NAK **");
                        	p = new Packet("NAK", "1");
                        	backward.send(p);
                            return 1;
                        }
                        System.out.println("         **Receiver(1->0): ok data; replying ACK **");
                        deliverToApp(packet.data);
                        p = new Packet("ACK", "1");
                        backward.send(p);
                        return 0;
                }
                return myState;
            }
    }

    /**
     * Runs rdt2.1 simulation
     * @param args	[-m pmunge][-l ploss][-f filename]
     * @throws IOException	if i/o error occurs
     */
    public static void main(String[] args) throws IOException {
        Object[] pargs = argParser("RDT21", args);
        RDT21 rdt21 = new RDT21((Double)pargs[0], (Double)pargs[1], (String)pargs[3]);
        rdt21.run();
    }

}
