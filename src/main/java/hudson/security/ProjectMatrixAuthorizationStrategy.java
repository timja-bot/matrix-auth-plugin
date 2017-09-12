/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Seiji Sogabe, Tom Huybrechts
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.security;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.model.AbstractItem;
import hudson.model.Descriptor;
import hudson.model.Node;
import jenkins.model.Jenkins;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.util.RobustReflectionConverter;
import hudson.Extension;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.core.JVM;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.matrixauth.AuthorizationMatrixNodeProperty;
import org.jenkinsci.plugins.matrixauth.Messages;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@link GlobalMatrixAuthorizationStrategy} plus per-project ACL.
 *
 * <p>
 * Per-project ACL is stored in {@link AuthorizationMatrixProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProjectMatrixAuthorizationStrategy extends GlobalMatrixAuthorizationStrategy {
    @Override
    public ACL getACL(Job<?,?> project) {
        AuthorizationMatrixProperty amp = project.getProperty(AuthorizationMatrixProperty.class);
        if (amp != null) {
            return amp.getInheritanceStrategy().getEffectiveACL(amp.getACL(), project);
        } else {
            return getACL(project.getParent());
        }
    }

    @Restricted(NoExternalUse.class)
    public static ACL inheritingACL(final ACL parent, final ACL child) {
        if (parent instanceof SidACL && child instanceof SidACL) {
            return ((SidACL) child).newInheritingACL((SidACL) parent);
        }
        return new ACL() {
            @Override
            public boolean hasPermission(Authentication a, Permission permission) {
                return child.hasPermission(a, permission) || parent.hasPermission(a, permission);
            }
        };
    }

    public ACL getACL(ItemGroup g) {
        if (g instanceof Item) {
            Item item = (Item) g;
            return item.getACL();
        }
        return getRootACL();
    }

    @Nonnull
    @Override
    public ACL getACL(@Nonnull Node node) {
        AuthorizationMatrixNodeProperty property = node.getNodeProperty(AuthorizationMatrixNodeProperty.class);
        if (property != null) {
            return property.getInheritanceStrategy().getEffectiveACL(property.getACL(), node);
        }
        return getRootACL();
    }

    @Override
    public ACL getACL(AbstractItem item) {
        if (Jenkins.getActiveInstance().getPlugin("cloudbees-folder") != null) { // optional dependency
            if (item instanceof AbstractFolder) {
                com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty p = (com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty) ((AbstractFolder) item).getProperties().get(com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty.class);
                if (p != null) {
                    return p.getInheritanceStrategy().getEffectiveACL(p.getACL(), item);
                }
            }
        }
        return getACL(item.getParent());
    }

    @Override
    public Set<String> getGroups() {
        Set<String> r = new TreeSet<String>(new IdStrategyComparator());
        r.addAll(super.getGroups());
        for (Job<?,?> j : Jenkins.getActiveInstance().getItems(Job.class)) {
            AuthorizationMatrixProperty amp = j.getProperty(AuthorizationMatrixProperty.class);
            if (amp != null)
                r.addAll(amp.getGroups());
        }
        return r;
    }

    @Extension
    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new DescriptorImpl() {
        @Override
        protected GlobalMatrixAuthorizationStrategy create() {
            return new ProjectMatrixAuthorizationStrategy();
        }

        @Override
        public String getDisplayName() {
            return Messages.ProjectMatrixAuthorizationStrategy_DisplayName();
        }
    };

    public static class ConverterImpl extends GlobalMatrixAuthorizationStrategy.ConverterImpl {

        @Override
        protected GlobalMatrixAuthorizationStrategy create() {
            return new ProjectMatrixAuthorizationStrategy();
        }

        @Override
        public boolean canConvert(Class type) {
            return type==ProjectMatrixAuthorizationStrategy.class;
        }
    }
}

