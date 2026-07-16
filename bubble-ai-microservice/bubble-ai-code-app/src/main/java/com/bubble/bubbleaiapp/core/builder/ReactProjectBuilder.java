package com.bubble.bubbleaiapp.core.builder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j @Component @RequiredArgsConstructor
public class ReactProjectBuilder {
    private final ReactGenerationProperties properties;
    private final BuildLogSanitizer sanitizer;

    public CompletableFuture<Boolean> buildProjectAsync(String path) { return buildProjectWithResultAsync(path).thenApply(BuildResult::success); }
    public CompletableFuture<BuildResult> buildProjectWithResultAsync(String path) {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> future.complete(buildProjectWithResult(path)));
        return future;
    }
    public boolean buildProject(String path) { return buildProjectWithResult(path).success(); }
    public BuildResult buildProjectWithResult(String path) {
        File dir = new File(path);
        if (!dir.isDirectory()) return invalid("validate project directory", "项目目录不存在");
        if (!new File(dir,"package.json").isFile()) return invalid("validate package.json", "package.json 文件不存在");
        if (!ensureEntry(dir)) return invalid("validate vite entry", "Vite 入口文件不存在或不可修复");
        BuildResult install = execute(dir,"npm_install",List.of(npm(),"install"),properties.getInstallTimeoutSeconds());
        if (!install.success()) return install;
        BuildResult build = execute(dir,"npm_build",List.of(npm(),"run","build"),properties.getBuildTimeoutSeconds());
        if (!build.success()) return build;
        return new File(dir,"dist").isDirectory() ? build : BuildResult.failure("validate_dist","validate dist",-1,build.stdout(),build.stderr(),"未生成 dist 目录",build.durationMillis(),false);
    }
    private BuildResult invalid(String command,String summary) { return BuildResult.failure("validation",command,-1,"","",summary,0,false); }
    private boolean ensureEntry(File dir) {
        File index = new File(dir,"index.html"), main = new File(dir,"src/main.jsx");
        if (!index.isFile() || !main.isFile()) return false;
        try {
            String html = Files.readString(index.toPath());
            if (!html.contains("src/main.jsx")) Files.writeString(index.toPath(), html.contains("</body>") ? html.replace("</body>","<script type=\"module\" src=\"/src/main.jsx\"></script></body>") : html+"<script type=\"module\" src=\"/src/main.jsx\"></script>");
            return true;
        } catch (Exception e) { return false; }
    }
    private BuildResult execute(File dir,String stage,List<String> parts,int timeout) {
        long start=System.nanoTime(); Process process=null; Buffer out=new Buffer(properties.getMaxBuildLogLength()/2), err=new Buffer(properties.getMaxBuildLogLength()/2);
        try {
            process=new ProcessBuilder(parts).directory(dir).start(); Process active=process;
            Thread o=Thread.ofVirtual().start(()->read(active.getInputStream(),out));
            Thread e=Thread.ofVirtual().start(()->read(active.getErrorStream(),err));
            boolean finished=process.waitFor(Math.max(1,timeout),TimeUnit.SECONDS);
            if(!finished){ process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
            o.join(2000); e.join(2000); long duration=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            BuildResult result = !finished ? BuildResult.failure(stage,String.join(" ",parts),-2,out.value(),err.value(),"构建超时",duration,true)
                    : process.exitValue()==0 ? BuildResult.success(stage,String.join(" ",parts),out.value(),err.value(),duration)
                    : BuildResult.failure(stage,String.join(" ",parts),process.exitValue(),out.value(),err.value(),"构建命令失败",duration,false);
            if(!result.success()) log.warn("React build failed, project={}, stage={}, exitCode={}, output={}",dir.getName(),stage,result.exitCode(),sanitizer.sanitize(result.output(),dir.getAbsolutePath(),6000));
            return result;
        } catch(InterruptedException e){ Thread.currentThread().interrupt(); if(process!=null)process.destroyForcibly(); return BuildResult.failure(stage,String.join(" ",parts),-3,out.value(),err.value(),"构建取消",0,false); }
        catch(Exception e){ if(process!=null)process.destroyForcibly(); return BuildResult.failure(stage,String.join(" ",parts),-1,out.value(),err.value(),"无法执行构建命令",0,false); }
    }
    private void read(InputStream input,Buffer buffer){ try(BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)buffer.add(line+"\n");}catch(Exception ignored){} }
    private String npm(){return System.getProperty("os.name").toLowerCase().contains("windows")?"npm.cmd":"npm";}
    private static final class Buffer{
        private final int max,headMax; private final StringBuilder head=new StringBuilder(); private final ArrayDeque<String> tail=new ArrayDeque<>(); private int tailSize; private long total;
        Buffer(int max){this.max=Math.max(2000,max);this.headMax=Math.min(2000,this.max/4);} synchronized void add(String s){total+=s.length();if(head.length()<headMax)head.append(s,0,Math.min(s.length(),headMax-head.length()));tail.add(s);tailSize+=s.length();while(tailSize>max-headMax&&tail.size()>1)tailSize-=tail.remove().length();}
        synchronized String value(){if(total<=headMax)return head.toString();StringBuilder b=new StringBuilder(head).append("\n... 日志已截断 ...\n");tail.forEach(b::append);return b.length()<=max?b.toString():b.substring(0,headMax)+"\n... 日志已截断 ...\n"+b.substring(b.length()-(max-headMax));}
    }
}
