package sc.junit;

import org.junit.*;
import org.junit.runner.*;

import static org.junit.Assert.*;

junit.lib {
   //classPath="./lib/junit-4.12.jar:./lib/hamcrest-core-1.3.jar";
   

   // These classes must be compiled so they can be used by the next layer up in the
   // chain.
   buildSeparate = true;
   compiledOnly = true;

   void initialize() {
      excludeRuntimes("js", "gwt", "android");
   }

   public void start() {
      sc.layer.LayeredSystem system = getLayeredSystem();
      sc.repos.RepositoryPackage pkg = addRepositoryPackage("junit", "mvn", "mvn://junit/junit/4.12",  false);
      if (pkg.installedRoot != null && !disabled) {
         classPath=pkg.classPath;
      }
   }
}
