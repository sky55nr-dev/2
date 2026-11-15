import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Local emulator ADB transport, using the existing developer key. No external network. */
public class DeviceAdb implements AutoCloseable {
 Socket socket;InputStream in;OutputStream out;int remote;static int id(String s){return ByteBuffer.wrap(s.getBytes()).order(ByteOrder.LITTLE_ENDIAN).getInt();}
 record Packet(int command,int a,int b,byte[] data){}
 DeviceAdb()throws Exception{socket=new Socket("127.0.0.1",5555);socket.setSoTimeout(120000);in=socket.getInputStream();out=socket.getOutputStream();send("CNXN",0x01000000,1048576,"host::\0".getBytes());Packet p=read();if(p.command==id("AUTH")){
  String pem=Files.readString(Path.of(System.getProperty("user.home"),".android","adbkey")).replaceAll("-----[^-]+-----","").replaceAll("\\s","");
  PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
  byte[] prefix=HexFormat.of().parseHex("3021300906052b0e03021a05000414");Signature signer=Signature.getInstance("NONEwithRSA");signer.initSign(key);signer.update(prefix);signer.update(p.data);send("AUTH",2,0,signer.sign());p=read();
 }if(p.command!=id("CNXN"))throw new IOException("ADB key not authorized: "+p.command);}
 void send(String cmd,int a,int b,byte[] data)throws Exception{int c=id(cmd),sum=0;for(byte x:data)sum+=x&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(c).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(c^0xffffffff);out.write(h.array());out.write(data);out.flush();}
 Packet read()throws Exception{byte[] header=in.readNBytes(24);if(header.length!=24)throw new EOFException();ByteBuffer h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);int c=h.getInt(),a=h.getInt(),b=h.getInt(),len=h.getInt();return new Packet(c,a,b,in.readNBytes(len));}
 void open(String service)throws Exception{send("OPEN",1,0,(service+"\0").getBytes());Packet p=read();if(p.command!=id("OKAY"))throw new IOException("Cannot open "+service);remote=p.a;}
 void write(byte[] data)throws Exception{for(int pos=0;pos<data.length;){int end=Math.min(data.length,pos+65536);send("WRTE",1,remote,Arrays.copyOfRange(data,pos,end));Packet p=read();if(p.command!=id("OKAY"))throw new IOException("Write not acknowledged");pos=end;}}
 void sync(String command,byte[] data)throws Exception{ByteBuffer b=ByteBuffer.allocate(8+data.length).order(ByteOrder.LITTLE_ENDIAN);b.put(command.getBytes()).putInt(data.length).put(data);write(b.array());}
 void push(String local,String target)throws Exception{open("sync:");sync("SEND",(target+",33206").getBytes());try(InputStream file=Files.newInputStream(Path.of(local))){byte[] b=new byte[65536];int n;while((n=file.read(b))!=-1)sync("DATA",Arrays.copyOf(b,n));}ByteBuffer b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);b.put("DONE".getBytes()).putInt((int)(System.currentTimeMillis()/1000));write(b.array());Packet p=read();if(p.command==id("WRTE")){send("OKAY",1,remote,new byte[0]);String status=new String(p.data);if(!status.startsWith("OKAY"))throw new IOException(status);}else throw new IOException("No push result");System.out.println("PUSH_OK");}
 void shell(String command)throws Exception{open("shell:"+command);for(;;){Packet p=read();if(p.command==id("WRTE")){System.out.write(p.data);send("OKAY",1,remote,new byte[0]);}else if(p.command==id("CLSE")){send("CLSE",1,remote,new byte[0]);break;}}}
 public void close()throws Exception{socket.close();}
 public static void main(String[] args)throws Exception{try(DeviceAdb adb=new DeviceAdb()){if(args[0].equals("push"))adb.push(args[1],args[2]);else adb.shell(args[1]);}}
}
