import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Reads permitted SDK bytes into memory to avoid Windows canonical-path failures in javac's ZIP provider. */
public final class CompileAndroid {
    static final class ApiClass extends SimpleJavaFileObject {
        final String binary; final byte[] data;
        ApiClass(String binary,byte[] data){super(URI.create("memory:///"+binary.replace('.','/')+".class"),Kind.CLASS);this.binary=binary;this.data=data;}
        public InputStream openInputStream(){return new ByteArrayInputStream(data);}
    }
    public static void main(String[] args)throws Exception{
        if(args.length<3)throw new IllegalArgumentException("android.jar output-directory source-files...");
        Map<String,List<JavaFileObject>> packages=new HashMap<>();Map<String,ApiClass> classes=new HashMap<>();
        try(ZipFile zip=new ZipFile(args[0])){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();String name=e.getName();
                if(!name.endsWith(".class") || !(name.startsWith("android/") || name.startsWith("org/json/") || name.startsWith("dalvik/")))continue;
                String binary=name.substring(0,name.length()-6).replace('/','.');
                try(InputStream stream=zip.getInputStream(e)){
                    ApiClass file=new ApiClass(binary,stream.readAllBytes());classes.put(binary,file);
                    String pkg=binary.substring(0,binary.lastIndexOf('.'));packages.computeIfAbsent(pkg,k->new ArrayList<>()).add(file);
                }
            }
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("JDK required");
        StandardJavaFileManager standard=compiler.getStandardFileManager(null,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8);
        JavaFileManager manager=new ForwardingJavaFileManager<StandardJavaFileManager>(standard){
            public Iterable<JavaFileObject> list(Location location,String packageName,Set<JavaFileObject.Kind> kinds,boolean recurse)throws IOException{
                if(location==StandardLocation.CLASS_PATH){
                    if(!kinds.contains(JavaFileObject.Kind.CLASS))return List.of();
                    if(!recurse)return packages.getOrDefault(packageName,List.of());
                    List<JavaFileObject> found=new ArrayList<>();packages.forEach((key,value)->{if(key.equals(packageName)||key.startsWith(packageName+"."))found.addAll(value);});return found;
                }
                return super.list(location,packageName,kinds,recurse);
            }
            public String inferBinaryName(Location l,JavaFileObject file){return file instanceof ApiClass?((ApiClass)file).binary:super.inferBinaryName(l,file);}
            public JavaFileObject getJavaFileForInput(Location l,String name,JavaFileObject.Kind kind)throws IOException{
                if(l==StandardLocation.CLASS_PATH&&kind==JavaFileObject.Kind.CLASS)return classes.get(name);
                return super.getJavaFileForInput(l,name,kind);
            }
            public boolean isSameFile(FileObject a,FileObject b){if(a instanceof ApiClass||b instanceof ApiClass)return a.toUri().equals(b.toUri());return super.isSameFile(a,b);}
        };
        Files.createDirectories(Path.of(args[1]));
        List<String> options=List.of("-d",args[1],"-encoding","UTF-8","-source","17","-target","17","-proc:none");
        Iterable<? extends JavaFileObject> units=standard.getJavaFileObjectsFromStrings(Arrays.asList(args).subList(2,args.length));
        boolean success=compiler.getTask(null,manager,null,options,null,units).call();
        manager.close();if(!success)System.exit(1);
        System.out.println("ANDROID_JAVA_COMPILE_OK ("+classes.size()+" SDK classes available)");
    }
}