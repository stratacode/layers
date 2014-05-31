/*
 * Copyright (c) 2009. Jeffrey Vroom
 */

import sc.dyn.DynUtil;
import sc.type.IBeanMapper;

/**
 * This is a lot like the VariableBinding but works without an initial source object.  
 * Instead, the first binding expression is used to produce the source.  This means that the
 * first expression can only be an independent binding, i.e. not a property mapping but a VariableBinding,
 * MethodBinding etc.
 */
public class SelectorBinding extends DestinationListener {
   IBinding [] boundProps;
   Object [] boundValues;

   volatile boolean valid = false;

   public boolean isValid() {
      return valid;
   }

   /** Use this form for chaining together this binding with another binding */
   public SelectorBinding(IBinding[] boundProperties) {
      boundProps = boundProperties;
      // We will always have at least two values - or else there is nothing to select on...
      assert boundProperties.length > 1;
   }

   /** Use this form for a top level binding */
   public SelectorBinding(Object dstObject, String dstProperty, IBinding[] boundProperties, BindingDirection bindingDirection) {
      this(boundProperties);
      dstObj = dstObject;
      dstProp = dstProperty;
      direction = bindingDirection;
   }

   /** Called by the parent when this is a hierarchical binding - i.e. one expression in another */
   public void setBindingParent(IBinding parent, BindingDirection dir) {
      dstProp = null;
      dstObj = parent;
      direction = dir;
      initBinding();
      addListeners();
   }

   /** Called to initialize and retrieve the value of a top-level binding */
   public Object initializeBinding() {
      assert dstObj != dstProp;

      initBinding();
      addListeners();
      if (direction.doForward()) {
         Object result = getBoundValue();
         if (Bind.trace)
            System.out.println("Init:" + toString());
         if (!isDefinedObject(result))
            result = null;
         return result;
      }
      else
         return null;
   }

   protected void initBinding() {
      Object bindingParent = null;
      boundProps[0].setBindingParent(this, direction);

      if (direction.doForward()) {
         boundValues = new Object[boundProps.length];
         boundValues[0] = boundProps[0].getPropertyValue(dstObj);
         bindingParent = boundValues[0];
      }
      int last = boundProps.length - 1;
      for (int i = 1; i <= last; i++) {
         boundProps[i].setBindingParent(this, direction);
         if (direction.doForward()) {
            if (isValidObject(bindingParent)) {
               boundValues[i] = bindingParent = boundProps[i].getPropertyValue(bindingParent);
            }
            else {
               boundValues[i] = bindingParent = UNSET_VALUE_SENTINEL;
            }
         }
      }
   }

   /** Returns the last property in the chain */
   private Object getBoundProperty() {
      return boundProps[boundProps.length-1];
   }

   /** Returns the parent of the last value */
   private Object getBoundParent() {
      if (direction.doForward())
         return boundValues[boundValues.length-2];
      else
         return evalBindingSlot(boundProps.length-2);
   }

   protected Object getBoundValue() {
      if (direction.doForward()) {
         return boundValues[boundValues.length-1];
      }
      return evalBinding();
   }

   private void addListeners() {
      if (sync == SyncType.ON_DEMAND) {
         if (direction.doForward())
            PBindUtil.addBindingListener(dstObj, dstProp, this, VALUE_REQUESTED);
         if (direction.doReverse()) {
            Object boundParent = getBoundParent();
            if (isValidObject(boundParent))
               PBindUtil.addBindingListener(boundParent, getBoundProperty(), this, VALUE_REQUESTED);
         }
      }

      // We always add the forward listeners so that we do not have to re-evaluate everything on demand
      if (direction.doForward()) {
         boundProps[0].addBindingListener(null, this, VALUE_CHANGED_MASK);
         for (int i = 0; i < boundValues.length-1; i++) {
            Object bv = boundValues[i];
            if (!isValidObject(bv))
                break;
            boundProps[i+1].addBindingListener(bv, this, VALUE_CHANGED_MASK);
            if (bv instanceof IChangeable) {
               Bind.addListener(bv, null, this, VALUE_CHANGED_MASK);
            }
         }
      }
      if (direction.doReverse()) {
         PBindUtil.addBindingListener(dstObj, dstProp, this, VALUE_CHANGED_MASK);
      }
   }

   boolean removed = false;

   public void removeListener() {
      if (removed) {
         System.err.println("*** removing a removed listener");
         return;
      }
      removed = true;
      if (sync == SyncType.ON_DEMAND) {
         if (direction.doForward())
            PBindUtil.removeBindingListener(dstObj, dstProp, this, VALUE_REQUESTED);
         if (direction.doReverse()) {
            Object boundParent = getBoundParent();
            if (isValidObject(boundParent))
               PBindUtil.removeBindingListener(boundParent, getBoundProperty(), this, VALUE_REQUESTED);
         }
      }
      if (direction.doForward()) {
         boundProps[0].removeBindingListener(null, this, VALUE_CHANGED_MASK);
         for (int i = 0; i < boundValues.length-1; i++) {
            Object bv = boundValues[i];
            if (!isValidObject(bv))
               break;

            // Remove listener for bindings which add explicit listeners
            boundProps[i+1].removeBindingListener(bv, this, VALUE_CHANGED_MASK);
            if (bv instanceof IChangeable) {
               Bind.removeListener(bv, null, this, VALUE_CHANGED_MASK);
            }

            // Remove listener for any stateful bindings
            boundProps[i].removeListener();
         }
      }
      if (direction.doReverse()) {
         PBindUtil.removeBindingListener(dstObj, dstProp, this, VALUE_CHANGED_MASK);
      }
   }

   public void valueRequested(Object obj, IBeanMapper prop) {
      if (sync == SyncType.ON_DEMAND) {
         // If the binding is out of date and the value requested is the right one.
         if (!valid) {
            synchronized (this) {
               if (!valid) {
                  if (obj == dstObj && prop == dstProp && direction.doForward()) {
                     applyBinding();
                  }
                  else {
                     Object boundObj = getBoundParent();
                     Object boundProp = getBoundProperty();
                     if (prop == boundProp && obj == boundObj && direction.doReverse())
                        applyReverseBinding();
                  }
               }
            }
         }
      }
   }

   public boolean valueInvalidated(Object srcObject, Object srcProp, Object eventDetail, boolean apply) {
      if (dstObj == srcObject && PBindUtil.equalProps(dstProp, srcProp)) {
         if (direction.doReverse()) {
            if (sync == SyncType.ON_DEMAND) {
               invalidate(true);
            }
         }
         return true; // ??? do we need to cache another value here so this is accurate
      }

      boolean changed = false;
      int i;

      if (direction.doForward()) {
         Object bindingParent = boundValues[0];
         Object lastParent = null;
         // Starting at the root binding, moving down the binding chain.  Look for the property which
         // this change event is for.  If the value has changed, update the listeners for all subsequent
         // elements since they may well have changed.
         for (i = 0; i < boundProps.length; i++) {
            if (changed || (PBindUtil.equalProps(srcProp, boundProps[i]) && srcObject == bindingParent)) {

               // This signals this binding that a previous binding has changed.  Any value cached by
               // a method binding needs to be re-evaluated.
               if (changed)
                  Bind.parentBindingChanged(boundProps[i]);

               Object newValue = !isValidObject(bindingParent) ? UNSET_VALUE_SENTINEL : boundProps[i].getPropertyValue(lastParent);

               if (!DynUtil.equalObjects(newValue, boundValues[i])) {
                  bindingParent = newValue;
                  changed = true;
               }
            }
            lastParent = bindingParent;
            bindingParent = boundValues[i];
         }

         if (changed) {
            if (sync == SyncType.ON_DEMAND) {
               invalidate(true);
            }
            else {
               if (!apply)
                  invalidateBinding(null, false, false);
            }
         }
      }
      return changed;
   }

   public boolean valueValidated(Object srcObject, Object srcProp, Object eventDetail, boolean apply) {
      if (dstObj == srcObject && PBindUtil.equalProps(dstProp, srcProp)) {
         if (direction.doReverse()) {
            if (sync != SyncType.ON_DEMAND) {
               if (apply)
                  applyReverseBinding();
            }
         }
         return true; // ??? do we need to cache another value here so this is accurate
      }

      boolean changed = false;
      int i;

      if (direction.doForward()) {
         Object bindingParent = boundValues[0];
         Object lastParent = null;
         // Starting at the root binding, moving down the binding chain.  Look for the property which
         // this change event is for.  If the value has changed, update the listeners for all subsequent
         // elements since they may well have changed.
         for (i = 0; i < boundProps.length; i++) {
            if (changed || (PBindUtil.equalProps(srcProp, boundProps[i]) && srcObject == bindingParent)) {
               Object newValue = !isValidObject(bindingParent) ? UNSET_VALUE_SENTINEL : boundProps[i].getPropertyValue(lastParent);

               if (!DynUtil.equalObjects(newValue, boundValues[i])) {
                  bindingParent = newValue;
                  changed = true;
                  valid = false;

                  if (apply) {
                     updateBoundValue(i, newValue);
                  }
               }
            }
            lastParent = bindingParent;
            bindingParent = boundValues[i];
         }

         if (changed) {
            if (sync != SyncType.ON_DEMAND) {
               if (apply)
                  applyBinding();
            }
         }
      }
      return changed;
   }

   private void updateBoundValue(int i, Object newValue) {
      if (i != boundProps.length-1) { // We don't listen for changes on the leaf property
         int next = i+1;
         Object tbv = boundValues[i];
         if (isValidObject(tbv))
            boundProps[next].removeBindingListener(tbv, this, VALUE_CHANGED_MASK);
         if (tbv instanceof IChangeable) {
            Bind.removeListener(tbv, null, this, VALUE_CHANGED_MASK);
         }

         if (isValidObject(newValue))
            boundProps[next].addBindingListener(newValue, this, VALUE_CHANGED_MASK);
         if (newValue instanceof IChangeable) {
            Bind.addListener(newValue, null, this, VALUE_CHANGED_MASK);
         }
      }
      boundValues[i] = newValue;
   }

   /** Used after reactivating... because we do not accurately track change events when deactivated we need to just do a re-validate.  It is also a nice operation to have for lazier evaluation schemes down the road. */
   private boolean validateBinding(Object obj) {
      if (direction.doForward() && !valid && activated) {
         Object bindingParent = boundValues[0];
         Object lastParent = obj;
         boolean changed = false;
         for (int i = 0; i < boundProps.length; i++) {
            Object newValue = !isValidObject(bindingParent) ? UNSET_VALUE_SENTINEL : boundProps[i].getPropertyValue(lastParent);

            if (!DynUtil.equalObjects(newValue, boundValues[i])) {
               updateBoundValue(i, newValue);
               changed = true;
            }
            lastParent = bindingParent;
            bindingParent = boundValues[i];
         }
         valid = true;
         return changed;
      }
      return false;
   }

   void applyBinding() {
      if (activated)
         valid = true;

      // TODO: Need to replace nulls with 0 for primitives?
      if (dstProp == null)
         ((IBinding) dstObj).applyBinding(null, getBoundValue(), this);
      else
         PBindUtil.setPropertyValue(dstObj, dstProp, getBoundValue());
   }

   private boolean applyReverseBinding() {
      Object bObj = getBoundParent();
      Object bProp = getBoundProperty();

      valid = true;
      Object newValue = PBindUtil.getPropertyValue(dstObj, dstProp);

      if (!DynUtil.equalObjects(newValue, getBoundValue())) {
         Bind.applyBinding(bObj, bProp, newValue, this);
         return true;
      }
      return false;
   }

   private Object evalBinding() {
      return evalBindingSlot(boundProps.length-1);
   }

   private Object evalBindingSlot(int last) {
      Object bindingParent = null;

      for (int i = 0; i <= last; i++) {
         bindingParent = boundProps[i].getPropertyValue(bindingParent);
         // The first time the bindingParent is null but it should be non-null for the second and subsequent
         if (!isValidObject(bindingParent))
            return last == boundProps.length - 1 ? null : UNSET_VALUE_SENTINEL; // return null for the last one as that's the real binding's value.  otherwise the binding would yield an NPE so we return UNSET
      }
      return bindingParent;
   }

   // Should only be used with ON_DEMAND sync'ing which only works when dstObj is IBindable
   private void invalidate(boolean sendEvent) {
      if (dstProp == null)
         ((IBinding) dstObj).invalidateBinding(dstObj, sendEvent, false);
      else if (sendEvent)
         Bind.sendEvent(IListener.VALUE_CHANGED, dstObj, (String)dstProp);
      valid = false;
   }

   public void invalidateBinding(Object obj, boolean sendEvent, boolean invalidateParams) {
      invalidate(sendEvent);
   }

   /**
    * One of our downstream values changed.  We need to figure out which one it was.
    * If the value changed, we need to re-evaluate all subsequent binding expressions after the one that
    * changed.
    */
   public boolean applyBinding(Object obj, Object value, IBinding src) {
      int i;
      boolean bound = false;
      Object bindingParent = null;
      for (i = 0; i < boundProps.length; i++) {
         if (src == boundProps[i]) {
            if (!DynUtil.equalObjects(boundValues[i], value)) {
               //boundValues[i] = value;
               updateBoundValue(i, value);
               bindingParent = value;
               bound = true;
            }
         }
         else if (bound) {
            Bind.parentBindingChanged(boundProps[i]);
            if (activated) {
               //boundValues[i] = bindingParent == null || bindingParent == UNSET_VALUE_SENTINEL ? UNSET_VALUE_SENTINEL : boundProps[i].getPropertyValue(bindingParent);
               updateBoundValue(i, boundValues[i] = !isValidObject(bindingParent) ? UNSET_VALUE_SENTINEL : boundProps[i].getPropertyValue(bindingParent));
            }
            bindingParent = boundValues[i];
         }
      }
      if (bound) {
         applyBinding();
         return true;
      }
      return false;
   }

   public void applyReverseBinding(Object obj, Object value, Object src) {
      int last = boundValues.length-1;
      if (!DynUtil.equalObjects(boundValues[last], value)) {
         boundValues[last] = value;
         boundProps[last].applyReverseBinding(getBoundParent(), value, this);
      }
   }

   public Object getPropertyValue(Object obj) {
      if (!valid)
         validateBinding(obj);
      return getBoundValue();
   }

   public void addBindingListener(Object eventObject, IListener listener, int event) {
      //for (IBinding param:boundParams)
      //   param.addBindingListener(eventObject, this, event);
   }

   public void removeBindingListener(Object eventObject, IListener listener, int event) {
      // TODO: remove???
      for (IBinding param:boundProps)
         param.removeBindingListener(eventObject, this, event);
   }

   public boolean isConstant() {
      return false;
   }

   public String toString(String operation, boolean displayValue) {
      StringBuilder sb = new StringBuilder();
      if (dstObj != dstProp && operation != null) {
         sb.append(operation);
         sb.append(" ");
      }
      sb.append(super.toString(operation, displayValue));
      for (int i = 0; i < boundProps.length; i++) {
         IBinding b = boundProps[i];
         if (i != 0)
            sb.append(".");
         sb.append(b.toString());
      }
      if (displayValue && dstObj != dstProp) {
         if (boundValues[boundProps.length-1] != null) {
            sb.append(" = ");
            sb.append(DynUtil.getInstanceName(boundValues[boundProps.length - 1]));
         }
         else {
            sb.append(" = null)");
         }
      }
      return sb.toString();
   }

   public void activate(boolean state, Object obj, boolean chained) {
      if (state == activated)
         return;
      super.activate(state, obj, chained);
      Object bindingParent = obj;
      int i = 0;
      for (IBinding param:boundProps) {
         param.activate(state, bindingParent, true);
         //if (i != boundProps.length - 1)
         //   if (state && bindingParent != null)
         //      bindingParent = param.getPropertyValue(bindingParent);
         i++;
      }

      if (state) {
         if (!valid && !chained)
            reactivate(obj);
      }
      else  // TODO: see TODO in VariableBinding - should be able to avoid this if we recode the activate process a little
         valid = false;
   }

   private void reactivate(Object obj) {
      Object bindingParent = null;
      if (direction.doForward()) {
         boundValues[0] = boundProps[0].getPropertyValue(obj);
         bindingParent = boundValues[0];
      }
      int last = boundProps.length - 1;
      for (int i = 1; i <= last; i++) {
         if (direction.doForward()) {
            Object oldValue = boundValues[i];
            if (isValidObject(bindingParent)) {
               boundValues[i] = bindingParent = boundProps[i].getPropertyValue(bindingParent);
            }
            else {
               boundValues[i] = bindingParent = UNSET_VALUE_SENTINEL;
            }
            if (i < last) {
               // Need to add/remove the listeners if the instance changed
               if ((!DynUtil.equalObjects(oldValue, boundValues[i]) || oldValue != boundValues[i])) {
                  if (isValidObject(oldValue)) {
                     boundProps[i+1].removeBindingListener(oldValue, this, VALUE_CHANGED_MASK);
                     if (oldValue instanceof IChangeable) {
                        Bind.removeListener(oldValue, null, this, VALUE_CHANGED_MASK);
                     }
                  }
                  Object newValue = boundValues[i];
                  if (isValidObject(newValue)) {
                     boundProps[i+1].addBindingListener(newValue, this, VALUE_CHANGED_MASK);
                     if (newValue instanceof IChangeable) {
                        Bind.addListener(newValue, null, this, VALUE_CHANGED_MASK);
                     }
                  }
               }
            }
         }
      }
      valid = true;
   }


   public int refreshBinding() {
      if (!activated)
         return 0;

      if (direction.doForward() && !direction.doReverse()) {
         valid = false;
         if (validateBinding(dstObj)) {
            applyBinding();
            return 1;
         }
         return 0;
      }
      else if (!direction.doForward() && direction.doReverse()) {
         if (applyReverseBinding()) 
            return 1;
      }
      return 0;
   }

   public boolean isReversible() {
      return true;
   }
}
