/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.v3.admin;

import com.sun.enterprise.admin.util.ClusterOperationUtil;
import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.util.LocalStringManagerImpl;
import java.beans.PropertyVetoException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import org.glassfish.api.admin.config.LegacyConfigurationUpgrade;
import org.glassfish.internal.api.Target;
import org.glassfish.api.ActionReport;
import javax.inject.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.BaseServiceLocator;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.component.PostConstruct;
import org.jvnet.hk2.config.*;
import org.jvnet.hk2.config.types.Property;
import org.jvnet.tiger_types.Types;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.*;

/**
 * User: Jerome Dochez
 * Date: Jul 11, 2008
 * Time: 4:39:05 AM
 */
@Service(name = "set")
@ExecuteOn(RuntimeType.INSTANCE)
@Scoped(PerLookup.class)
@I18n("set")
public class SetCommand extends V2DottedNameSupport implements AdminCommand, PostConstruct {

    @Inject
    BaseServiceLocator habitat;

    @Inject
    Applications app; 

    @Inject
    Domain domain;

    @Inject
    ConfigSupport config;

    @Inject
    Target targetService;

    @Param(primary = true, multiple = true)
    String[] values;
    final private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(SetCommand.class);

    private HashMap<String, Integer> targetLevel = null;

    @Override
    public void postConstruct() {
        targetLevel = new HashMap<String, Integer>();
        targetLevel.put("applications", 0);
        targetLevel.put("system-applications", 0);
        targetLevel.put("resources", 0);
        targetLevel.put("configs", 3);
        targetLevel.put("clusters", 3);
        targetLevel.put("servers", 3);
        targetLevel.put("nodes", 3);
    }

    @Override
    public void execute(AdminCommandContext context) {
        for (String value : values) {

            if (value.contains(".log-service")) {
                fail(context, localStrings.getLocalString("admin.set.invalid.logservice.command", "For setting log levels/attributes use set-log-levels/set-log-attributes command."));
                return;
            }

            if (!set(context, value))
                return;
        }
    }

    /**
     * author Jeremy_Lv
     * @param value
     * @return
     */
    private boolean judgecontextroot(AdminCommandContext context,String value){
        int app_size = app.getApplications().size();
        HashSet<String> appName = new HashSet();
        if(value.contains("context-root")){
        	int appIndex = value.indexOf("application.");
        	appIndex = appIndex + 12;
        	int flag = value.indexOf(".context-root=/");
        	String modulename = value.substring(appIndex, flag);
            flag=flag+14;
            String contextroot = value.substring(flag);
            for(int i = 0;i < app_size;i++ ){
                if(modulename.equals(app.getApplications().get(i).getName())){
                    appName = judgetargetdulicated(app.getApplications().get(i).getName());
                    break;
                    }
            }
            Iterator<String> it=appName.iterator();
            while(it.hasNext()){
                String appname = it.next();
                for(int i = 0;i<app_size;i++ ){
                    if(appname.equals(app.getApplications().get(i).getName())){
                        if(contextroot.equals(app.getApplications().get(i).getContextRoot())){
                            fail(context, localStrings.getLocalString("admin.set.contextroot.duplicated",":Virtual server already has a web module {0} loaded at {1}; therefore this web module cannot be loaded at this context.",appname,contextroot));
                            return true;
                            }
                        }
                    }
            }
        }
        return false;
    }

    /**
     * author Jeremy_Lv
     * @param value
     * @return
     */
    private HashSet<String> judgetargetdulicated(String modulename){
        int serv_size = domain.getServers().getServer().size();
        HashSet<String> appName = new HashSet();
        for(int i= 0;i<serv_size;i++){
            for(int j=0;j<domain.getServers().getServer().get(i).getApplicationRef().size();j++){
                if(modulename.equals(domain.getServers().getServer().get(i).getApplicationRef().get(j).getRef())){
                    for(int z=0;z<domain.getServers().getServer().get(i).getApplicationRef().size();z++){
                        appName.add(domain.getServers().getServer().get(i).getApplicationRef().get(z).getRef());
                        }
                    }
                }
            }
        return appName;
    }

    private boolean set(AdminCommandContext context, String nameval) {

        int i = nameval.indexOf('=');
        if (i < 0) {
            //ail(context, "Invalid attribute " + nameval);
            fail(context, localStrings.getLocalString("admin.set.invalid.namevalue", "Invalid name value pair {0}. Missing expected equal sign.", nameval));
            return false;
        }
        String target = nameval.substring(0, i);
        String value = nameval.substring(i + 1);
        // so far I assume we always want to change one attribute so I am removing the
        // last element from the target pattern which is supposed to be the
        // attribute name
        int lastDotIndex = trueLastIndexOf(target, '.');
        if (lastDotIndex == -1) {
            // error.
            //fail(context, "Invalid attribute name " + target);
            fail(context, localStrings.getLocalString("admin.set.invalid.attributename", "Invalid attribute name {0}", target));
            return false;
        }
        String attrName = target.substring(lastDotIndex + 1).replace("\\.", ".");
        String pattern = target.substring(0, lastDotIndex);
        if (attrName.replace('_', '-').equals("jndi-name")) {
            //fail(context, "Cannot change a primary key\nChange of " + target + " is rejected.");
            fail(context, localStrings.getLocalString("admin.set.reject.keychange", "Cannot change a primary key\nChange of {0}", target));
            return false;
        }
        boolean isProperty = false;
        if ("property".equals(pattern.substring(trueLastIndexOf(pattern, '.') + 1))) {
            // we are looking for a property, let's look it it exists already...
            pattern = target.replaceAll("\\\\\\.", "\\.");
            isProperty = true;
        }

        // now
        // first let's get the parent for this pattern.
        TreeNode[] parentNodes = getAliasedParent(domain, pattern);

        // reset the pattern.
        String prefix;
        boolean lookAtSubNodes = true;
        if (parentNodes[0].relativeName.length() == 0 ||
                parentNodes[0].relativeName.equals("domain")) {
            // handle the case where the pattern references an attribute of the top-level node
            prefix = "";
            // pattern is already set properly
            lookAtSubNodes = false;
        }
        else if(!pattern.startsWith(parentNodes[0].relativeName)) {
            prefix = pattern.substring(0, pattern.indexOf(parentNodes[0].relativeName));
            pattern = parentNodes[0].relativeName;
        }
        else {
            prefix = "";
            pattern = parentNodes[0].relativeName;
        }
        String targetName = prefix + pattern;

        Map<Dom, String> matchingNodes;
        boolean applyOverrideRules = false;
        Map<Dom, String> dottedNames = new HashMap<Dom, String>();
        if (lookAtSubNodes) {
            for (TreeNode parentNode : parentNodes) {
                dottedNames.putAll(getAllDottedNodes(parentNode.node));
            }
            matchingNodes = getMatchingNodes(dottedNames, pattern);
            applyOverrideRules = true;
        } else {
            matchingNodes = new HashMap<Dom, String>();
            for (TreeNode parentNode : parentNodes) {
                matchingNodes.put(parentNode.node, pattern);
            }
        }

        if (matchingNodes.isEmpty()) {
            // it's possible they are trying to create a property object.. lets check this.
            // strip out the property name
            pattern = target.substring(0, trueLastIndexOf(target, '.'));
            if (pattern.endsWith("property")) {
                pattern = pattern.substring(0, trueLastIndexOf(pattern, '.'));
                parentNodes = getAliasedParent(domain, pattern);
                pattern = parentNodes[0].relativeName;
                matchingNodes = getMatchingNodes(dottedNames, pattern);
                if (matchingNodes.isEmpty()) {
                    //fail(context, "No configuration found for " + targetName);
                    fail(context, localStrings.getLocalString("admin.set.configuration.notfound", "No configuration found for {0}", targetName));
                    return false;
                }
                // need to find the right parent.
                Dom parentNode = null;
                for (Map.Entry<Dom, String> node : matchingNodes.entrySet()) {
                    if (node.getValue().equals(pattern)) {
                        parentNode = node.getKey();
                    }
                }
                if (parentNode == null) {
                    //fail(context, "No configuration found for " + targetName);
                    fail(context, localStrings.getLocalString("admin.set.configuration.notfound", "No configuration found for {0}", targetName));
                    return false;
                }

                if (value == null || value.length() == 0) {
                    // setting to the empty string means to remove the property, so don't create it
                    success(context, targetName, value);
                    return true;
                }
                // create and set the property
                Map<String, String> attributes = new HashMap<String, String>();
                attributes.put("value", value);
                attributes.put("name", attrName);
                try {
                    ConfigSupport.createAndSet((ConfigBean) parentNode, Property.class, attributes);
                    success(context, targetName, value);
                    runLegacyChecks(context);
                    if (targetService.isThisDAS() && !replicateSetCommand(context, targetName, value))
                        return false;
                    return true;
                } catch (TransactionFailure transactionFailure) {
                    //fail(context, "Could not change the attributes: " +
                    //    transactionFailure.getMessage(), transactionFailure);
                    fail(context, localStrings.getLocalString("admin.set.attribute.change.failure", "Could not change the attributes: {0}",
                            transactionFailure.getMessage()), transactionFailure);
                    return false;
                }
            }
        }

        Map<ConfigBean, Map<String, String>> changes = new HashMap<ConfigBean, Map<String, String>>();

        boolean setElementSuccess = false;
        boolean delPropertySuccess = false;
        boolean delProperty = false;
        Map<String, String> attrChanges = new HashMap<String, String>();
        if (isProperty) {
            attrName = "value";
            if ((value == null) || (value.length() == 0)) {
                delProperty = true;
            }
            attrChanges.put(attrName, value);
        }

        List<Map.Entry> mNodes = new ArrayList(matchingNodes.entrySet());
        if (applyOverrideRules) {
            mNodes = applyOverrideRules(mNodes);
        }
        for (Map.Entry<Dom, String> node : mNodes) {
            final Dom targetNode = node.getKey();

            for (String name : targetNode.model.getAttributeNames()) {
                String finalDottedName = node.getValue() + "." + name;
                if (matches(finalDottedName, pattern)) {
                    if (attrName.equals(name) ||
                            attrName.replace('_', '-').equals(name.replace('_', '-')))  {
                        if (isDeprecatedAttr(targetNode, name)) {
                           warning(context, localStrings.getLocalString("admin.set.deprecated",
                                   "Warning: The attribute {0} is deprecated.", finalDottedName));
                        }

                        if (!isProperty) {
                            targetName = prefix + finalDottedName;

                            if (value != null && value.length() > 0) {
                                attrChanges.put(name, value);
                            } else {
                                attrChanges.put(name, null);
                            }
                        } else {
                            targetName = prefix + node.getValue();
                        }

                        if (delProperty) {
                            // delete property element
                            String str = node.getValue();
                            if (trueLastIndexOf(str, '.') != -1) {
                                str = str.substring(trueLastIndexOf(str, '.') + 1);
                            }
                            try {
                                if (str != null) {
                                    ConfigSupport.deleteChild((ConfigBean) targetNode.parent(), (ConfigBean) targetNode);
                                    delPropertySuccess = true;
                                }
                            } catch (IllegalArgumentException ie) {
                                fail(context, localStrings.getLocalString("admin.set.delete.property.failure", "Could not delete the property: {0}",
                                        ie.getMessage()), ie);
                                return false;
                            } catch (TransactionFailure transactionFailure) {
                                fail(context, localStrings.getLocalString("admin.set.attribute.change.failure", "Could not change the attributes: {0}",
                                        transactionFailure.getMessage()), transactionFailure);
                                return false;
                            }
                        } else {
                            changes.put((ConfigBean) node.getKey(), attrChanges);
                        }

                    }
                }
            }

            for (String name : targetNode.model.getLeafElementNames()) {
                String finalDottedName = node.getValue() + "." + name;
                if (matches(finalDottedName, pattern)) {
                    if (attrName.equals(name) ||
                            attrName.replace('_', '-').equals(name.replace('_', '-')))  {
                        if (isDeprecatedAttr(targetNode, name)) {
                           warning(context, localStrings.getLocalString("admin.set.elementdeprecated",
                                   "Warning: The element {0} is deprecated.", finalDottedName));
                        }
                        try {
                            setLeafElement((ConfigBean)targetNode, name, value);
                        } catch (TransactionFailure ex) {
                            fail(context, localStrings.getLocalString("admin.set.badelement", "Cannot change the element: {0}",
                                    ex.getMessage()), ex);
                            return false;
                        }
                        setElementSuccess = true;
                        break;
                    }
                }
            }
        }
        if (!changes.isEmpty()) {
            try {
                if(!judgecontextroot(context,nameval)){
                    config.apply(changes);
                    success(context, targetName, value);
                    runLegacyChecks(context);
                }else{
                    return false;
                }
            } catch (TransactionFailure transactionFailure) {
                //fail(context, "Could not change the attributes: " +
                //        transactionFailure.getMessage(), transactionFailure);
                fail(context, localStrings.getLocalString("admin.set.attribute.change.failure", "Could not change the attributes: {0}",
                        transactionFailure.getMessage()), transactionFailure);
                return false;
            }

        } else if (delPropertySuccess || setElementSuccess) {
            success(context, targetName, value);
        } else {
            fail(context, localStrings.getLocalString("admin.set.configuration.notfound", "No configuration found for {0}", targetName));
            return false;
        }
        if (targetService.isThisDAS() && !replicateSetCommand(context, targetName, value))
            return false;
        return true;
    }

    public static void setLeafElement (
                final ConfigBean node,
                final String elementName,
                final String values)
        throws TransactionFailure {

        ConfigBeanProxy readableView = node.getProxy(node.getProxyType());
        ConfigSupport.apply(new SingleConfigCode<ConfigBeanProxy>() {

            /**
             * Runs the following command passing the configuration object. The code will be run
             * within a transaction, returning true will commit the transaction, false will abort
             * it.
             *
             * @param param is the configuration object protected by the transaction
             * @return any object that should be returned from within the transaction code
             * @throws java.beans.PropertyVetoException
             *          if the changes cannot be applied
             *          to the configuration
             */
            @Override
            public Object run(ConfigBeanProxy param) throws PropertyVetoException, TransactionFailure {

                WriteableView writeableParent = (WriteableView)Proxy.getInvocationHandler(param);

                StringTokenizer st = new StringTokenizer(values, ",");
                List<String> valList = new ArrayList<String>();
                while (st.hasMoreTokens()) valList.add(st.nextToken());

                ConfigBean bean = writeableParent.getMasterView();
                for (Method m : writeableParent.getProxyType().getMethods()) {
                    // Check to see if the method is a setter for the element
                    // An element setter has to have the right name, take a single
                    // collection parameter that parameterized with the right type
                    Class argClasses[] = m.getParameterTypes();
                    Type argTypes[] = m.getGenericParameterTypes();
                    if (!bean.model.toProperty(m).xmlName().equals(elementName) ||
                            argClasses.length != 1 ||
                            !Collection.class.isAssignableFrom(argClasses[0]) ||
                            argTypes.length != 1 ||
                            !(argTypes[0] instanceof ParameterizedType) ||
                            !Types.erasure(Types.getTypeArgument(argTypes[0], 0)).isAssignableFrom(values.getClass())) {
                        continue;
                    }
                    // we have the right method.  Now call it
                    try {
                        m.invoke(writeableParent.getProxy(writeableParent.<ConfigBeanProxy>getProxyType()), valList);
                    } catch (IllegalAccessException e) {
                        throw new TransactionFailure("Exception while setting element", e);
                    } catch (InvocationTargetException e) {
                        throw new TransactionFailure("Exception while setting element", e);
                    }
                    return node;
                }
                throw new TransactionFailure("No method found for setting element");
            }
        }, readableView);
    }

    /*
     * Determine whether this attribute is deprecated.  This method
     * stops looking after it finds the first method or field whose name matches
     * the attribute name. So to make an attribute deprecated, all of the
     * methods (set, get, etc.) must be marked as deprecated.
     */
    private boolean isDeprecatedAttr(Dom dom, String name) {
        if (dom == null || dom.model == null || name == null) return false;
        Class t = dom.getProxyType();
        if (t == null) return false;
        for (Method m : t.getDeclaredMethods()) {
            ConfigModel.Property p = dom.model.toProperty(m);
            if (p != null && name.equals(p.xmlName())) {
                return m.isAnnotationPresent(Deprecated.class);
            }
        }
        for (Field f : t.getDeclaredFields()) {
            if (name.equals(dom.model.camelCaseToXML(f.getName()))) {
                return f.isAnnotationPresent(Deprecated.class);
            }
        }
        return false;
    }

    private String getElementFromString(String name, int index) {
        StringTokenizer token = new StringTokenizer(name, ".");
        String target = null;
        for (int j = 0; j < index; j++) {
            if (token.hasMoreTokens())
                target = token.nextToken();
        }
        return target;
    }

    private boolean replicateSetCommand(AdminCommandContext context, String targetName, String value) {
        // "domain." on the front of the attribute name is optional.  So if it is
        // there, strip it off. 
        List<Server> replicationInstances = null;
        String tName;
        if (targetName.startsWith("domain.")) {
            tName = targetName.substring("domain.".length());
            if (tName.indexOf('.') == -1) {
                // This is a domain-level attribute, replicate to all instances
                replicationInstances = targetService.getAllInstances();
            }
        }
        else {
            tName = targetName;
        }
        if (replicationInstances == null) {
            int dotIdx = tName.indexOf('.');
            String firstElementOfName = dotIdx != -1 ? tName.substring(0, dotIdx) : tName;
            Integer targetElementLocation = targetLevel.get(firstElementOfName);
            if (targetElementLocation == null)
                targetElementLocation = 1;
            if (targetElementLocation == 0) {
                if ("resources".equals(firstElementOfName)) {
                    replicationInstances = targetService.getAllInstances();
                }
                if ("applications".equals(firstElementOfName)) {
                    String appName = getElementFromString(tName, 3);
                    if (appName == null) {
                        fail(context, localStrings.getLocalString("admin.set.invalid.appname",
                                "Unable to extract application name from {0}", targetName));
                        return false;
                    }
                    replicationInstances = targetService.getInstances(domain.getAllReferencedTargetsForApplication(appName));
                }
            } else {
                String target = getElementFromString(tName, targetElementLocation);
                if (target == null) {
                    fail(context, localStrings.getLocalString("admin.set.invalid.target",
                            "Unable to extract replication target from {0}", targetName));
                    return false;
                }
                replicationInstances = targetService.getInstances(target);
            }
        }

        if (replicationInstances != null && !replicationInstances.isEmpty()) {
            ParameterMap params = new ParameterMap();
            params.set("DEFAULT", targetName + "=" + value);
            ActionReport.ExitCode ret = ClusterOperationUtil.replicateCommand("set", FailurePolicy.Error,
                    FailurePolicy.Warn, FailurePolicy.Ignore, replicationInstances, context, params, habitat);
            if (ret.equals(ActionReport.ExitCode.FAILURE))
                return false;
        }
        return true;
    }

    private void runLegacyChecks(AdminCommandContext context) {
        final Collection<LegacyConfigurationUpgrade> list = habitat.getAllByContract(LegacyConfigurationUpgrade.class);
        for (LegacyConfigurationUpgrade upgrade : list) {
            upgrade.execute(context);
        }
    }

    /**
     * Find the rightmost unescaped occurrence of specified character in target
     * string.
     * <p/>
     * XXX Doesn't correctly interpret escaped backslash characters, e.g. foo\\.bar
     *
     * @param target string to search
     * @param ch     a character
     * @return index index of last unescaped occurrence of specified character
     *         or -1 if there are no unescaped occurrences of this character.
     */
    private static int trueLastIndexOf(String target, char ch) {
        int i = target.lastIndexOf(ch);
        while (i > 0) {
            if (target.charAt(i - 1) == '\\') {
                i = target.lastIndexOf(ch, i - 1);
            } else {
                break;
            }
        }
        return i;
    }

    /**
     * Indicate in the action report that the command failed.
     */
    private static void fail(AdminCommandContext context, String msg) {
        fail(context, msg, null);
    }

    /**
     * Indicate in the action report that the command failed.
     */
    private static void fail(AdminCommandContext context, String msg,
                             Exception ex) {
        context.getActionReport().setActionExitCode(
                ActionReport.ExitCode.FAILURE);
        if (ex != null)
            context.getActionReport().setFailureCause(ex);
        context.getActionReport().setMessage(msg);
    }

    /**
     * Indicate in the action report a warning message.
     */
    private void warning(AdminCommandContext context, String msg) {
        ActionReport ar = context.getActionReport().addSubActionsReport();
        ar.setActionExitCode(ActionReport.ExitCode.WARNING);
        ar.setMessage(msg);
    }

    /**
     * Indicate in the action report that the command succeeded and
     * include the target property and it's value in the report
     */
    private void success(AdminCommandContext context, String target, String value) {
        context.getActionReport().setActionExitCode(ActionReport.ExitCode.SUCCESS);
        ActionReport.MessagePart part = context.getActionReport().getTopMessagePart().addChild();
        part.setChildrenType("DottedName");
        part.setMessage(target + "=" + value);
    }
}
