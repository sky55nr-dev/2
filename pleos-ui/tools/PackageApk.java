import java.util.*;import java.util.zip.*;import java.nio.file.*;import java.io.*;
public class PackageApk {
 static void put(ZipOutputStream out,String name,byte[] bytes)throws Exception{ZipEntry e=new ZipEntry(name);if(name.equals("resources.arsc")||name.endsWith(".so")){CRC32 crc=new CRC32();crc.update(bytes);e.setMethod(ZipEntry.STORED);e.setSize(bytes.length);e.setCompressedSize(bytes.length);e.setCrc(crc.getValue());}out.putNextEntry(e);out.write(bytes);out.closeEntry();}
 public static void main(String[] args)throws Exception{try(ZipFile original=new ZipFile(args[0]);ZipFile manifest=new ZipFile(args[1]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[3])))){
  int dex=1;for(ZipEntry e:Collections.list(original.entries())){String name=e.getName();if(e.isDirectory()||name.equals("AndroidManifest.xml")||name.equals("META-INF/MANIFEST.MF")||name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)"))continue;if(name.matches("classes[0-9]*\\.dex")){String index=name.substring(7,name.length()-4);dex=Math.max(dex,(index.isEmpty()?1:Integer.parseInt(index))+1);}put(out,name,original.getInputStream(e).readAllBytes());}
  put(out,"AndroidManifest.xml",manifest.getInputStream(manifest.getEntry("AndroidManifest.xml")).readAllBytes());
  try(var files=Files.list(Path.of(args[2]))){for(Path file:files.filter(p->p.toString().endsWith(".dex")).sorted().toList())put(out,"classes"+(dex++)+".dex",Files.readAllBytes(file));}
 }}
}
