"use components";
import { random } from './wasi-random.js';
const { getRandomBytes } = random;

if (getRandomBytes=== undefined) {
  const err = new Error("unexpectedly undefined local import 'getRandomBytes', was 'getRandomBytes' available at instantiation?");
  console.error("ERROR:", err.toString());
  throw err;
}


const emptyFunc = () => {};

let dv = new DataView(new ArrayBuffer());
const dataView = mem => dv.buffer === mem.buffer ? dv : dv = new DataView(mem.buffer);

function toInt64(val) {
  const converted = BigInt(val)
  
  return BigInt.asIntN(64, converted);
}


function toInt32(val) {
  
  return val >> 0;
}


function toUint32(val) {
  
  return val >>> 0;
}


function toUint8(val) {
  
  val >>>= 0;
  val %= 2 ** 8;
  return val;
}


function _isValidNumericPrimitive(ty, v) {
  if (v === undefined || v === null) { return false; }
  switch (ty) {
    case 'bool':
    return v === 0 || v === 1;
    break;
    case 'u8':
    return typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 255;
    break;
    case 's8':
    return typeof v === 'number' && Number.isInteger(v) && v >= -128 && v <= 127;
    break;
    case 'u16':
    return typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 65535;
    break;
    case 's16':
    return typeof v === 'number' && Number.isInteger(v) && v >= -32768 && v <= 32767;
    case 'u32':
    return typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 4_294_967_295;
    case 's32':
    return typeof v === 'number' && Number.isInteger(v) && v >= -2_147_483_648 && v <= 2_147_483_647;
    case 'u64':
    return typeof v === 'bigint' && v >= 0 && v <= 18_446_744_073_709_551_615n;
    case 's64':
    return typeof v === 'bigint' && v >= -9223372036854775808n && v <= 9223372036854775807n;
    break;
    case 'f32':
    case 'f64': return typeof v === 'number';
    default:
    return false;
  }
  return true;
}

function _requireValidNumericPrimitive(ty, v) {
  if (v === undefined  || v === null || !_isValidNumericPrimitive(ty, v)) {
    throw new TypeError(`invalid ${ty} value [${v}]`);
  }
  return true;
}
const TEXT_DECODER_UTF8 = new TextDecoder();
const TEXT_ENCODER_UTF8 = new TextEncoder();

function _utf8AllocateAndEncode(s, realloc, memory) {
  if (typeof s !== 'string') {
    throw new TypeError('expected a string, received [' + typeof s + ']');
  }
  if (s.length === 0) { return { ptr: 1, len: 0 }; }
  // Compute the exact allocation size up front. Some older preview1
  // adapters only support an initial allocation, not a subsequent shrink.
  let len = 0;
  let codepoints = 0;
  for (let i = 0; i < s.length; i++) {
    const ch = s.charCodeAt(i);
    codepoints++;
    if (ch < 0x80) { len += 1; }
    else if (ch < 0x800) { len += 2; }
    else if (ch >= 0xd800 && ch <= 0xdbff &&
    i + 1 < s.length &&
    (s.charCodeAt(i + 1) & 0xfc00) === 0xdc00) {
      len += 4;
      i++;
    } else { len += 3; }
  }
  const ptr = realloc(0, 0, 1, len);
  const { read, written } = TEXT_ENCODER_UTF8.encodeInto(
  s,
  new Uint8Array(memory.buffer, ptr, len),
  );
  if (read !== s.length || written !== len) {
    throw new Error('failed to encode whole string');
  }
  const res = { ptr, len, codepoints };
  return res;
}

const T_FLAG = 1 << 30;

function rscTableCreateOwn(table, rep) {
  const free = table[0] & ~T_FLAG;
  table._createdReps.add(rep);
  if (free === 0) {
    table.push(0);
    table.push(rep | T_FLAG);
    return (table.length >> 1) - 1;
  }
  table[0] = table[free << 1];
  table[free << 1] = 0;
  table[(free << 1) + 1] = rep | T_FLAG;
  return free;
}

const WebAssemblyRuntimeError = WebAssembly.RuntimeError;

function rscTableGet(table, handle) {
  const scope = table[handle << 1];
  const val = table[(handle << 1) + 1];
  const own = (val & T_FLAG) !== 0;
  const rep = val & ~T_FLAG;
  if (rep === 0 || (scope & T_FLAG) !== 0) {
    // Resource entries occupy scope/rep pairs after the table sentinel.
    throw new WebAssemblyRuntimeError(`unknown handle index ${(handle << 1) + 1}`);
  }
  return { rep, scope, own };
}

const RESOURCE_SCOPE_TASKS = new Map();

function rscTableRemove(table, handle) {
  const scope = table[handle << 1];
  const val = table[(handle << 1) + 1];
  const own = (val & T_FLAG) !== 0;
  const rep = val & ~T_FLAG;
  if (val === 0 || (scope & T_FLAG) !== 0) {
    // Resource entries occupy scope/rep pairs after the table sentinel.
    throw new WebAssemblyRuntimeError(`unknown handle index ${(handle << 1) + 1}`);
  }
  if (own && scope !== 0) {
    throw new WebAssemblyRuntimeError('cannot remove owned resource while borrowed');
  }
  const borrowTask = own ? undefined : RESOURCE_SCOPE_TASKS.get(scope);
  table[handle << 1] = table[0] | T_FLAG;
  table[0] = handle | T_FLAG;
  borrowTask?.removeBorrowedHandle();
  return { rep, scope, own };
}

let RESOURCE_SCOPE_ID = 0;
const ASYNC_TASKS_BY_COMPONENT_IDX = new Map();
const ASYNC_CURRENT_COMPONENT_IDXS = [];

function getCurrentTask(componentIdx, taskID) {
  let usedGlobal = false;
  if (componentIdx === undefined || componentIdx === null) {
    throw new Error('missing component idx'); // TODO(fix)
    // componentIdx = ASYNC_CURRENT_COMPONENT_IDXS.at(-1);
    // usedGlobal = true;
  }
  
  const taskMetas = ASYNC_TASKS_BY_COMPONENT_IDX.get(componentIdx);
  if (taskMetas === undefined || taskMetas.length === 0) { return undefined; }
  
  if (taskID) {
    return taskMetas.find(meta => meta.task.id() === taskID);
  }
  
  const taskMeta = taskMetas[taskMetas.length - 1];
  if (!taskMeta || !taskMeta.task) { return undefined; }
  
  return taskMeta;
}
const ASYNC_CURRENT_TASK_IDS = [];

const _debugLog = (...args) => {
  if (!globalThis?.process?.env?.JCO_DEBUG) { return; }
  console.debug(...args);
};

function clearCurrentTask(componentIdx, taskID) {
  _debugLog('[clearCurrentTask()] args', { componentIdx, taskID });
  
  if (componentIdx === undefined || componentIdx === null) {
    throw new Error('missing/invalid component instance index while ending current task');
  }
  
  const tasks = ASYNC_TASKS_BY_COMPONENT_IDX.get(componentIdx);
  if (!tasks || !Array.isArray(tasks)) {
    throw new Error('missing/invalid tasks for component instance while ending task');
  }
  if (tasks.length == 0) {
    throw new Error(`no current tasks for component instance [${componentIdx}] while ending task`);
  }
  
  if (taskID !== undefined) {
    const last = tasks[tasks.length - 1];
    if (last.id !== taskID) {
      // throw new Error('current task does not match expected task ID');
      return;
    }
  }
  
  ASYNC_CURRENT_TASK_IDS.pop();
  ASYNC_CURRENT_COMPONENT_IDXS.pop();
  
  const taskMeta = tasks.pop();
  return taskMeta.task;
}
const ASYNC_STATE = new Map();

function promiseWithResolvers() {
  if (Promise.withResolvers) {
    return Promise.withResolvers();
  } else {
    let resolve;
    let reject;
    const promise = new Promise((res, rej) => {
      resolve = res;
      reject = rej;
    });
    return { promise, resolve, reject };
  }
}

class Waitable {
  #componentIdx;
  
  #pendingEventFn = null;
  
  #promise;
  #resolve;
  #reject;
  
  #waitableSet = null;
  
  #hasSyncWaiter = false;
  
  #idx = null; // to component-global waitables
  
  target;
  
  constructor(args) {
    const { componentIdx, target } = args;
    this.#componentIdx = componentIdx;
    this.target = args.target;
    this.#resetPromise();
  }
  
  componentIdx() { return this.#componentIdx; }
  isInSet() { return this.#waitableSet !== null; }
  
  idx() { return this.#idx; }
  setIdx(idx) {
    if (idx === 0) { throw new Error("waitable idx cannot be zero"); }
    this.#idx = idx;
  }
  
  setTarget(tgt) { this.target = tgt; }
  
  #resetPromise() {
    const { promise, resolve, reject } = promiseWithResolvers()
    this.#promise = promise;
    this.#resolve = resolve;
    this.#reject = reject;
  }
  
  resolve() { this.#resolve(); }
  reject(err) { this.#reject(err); }
  promise() { return this.#promise; }
  
  hasPendingEvent() {
    // _debugLog('[Waitable#hasPendingEvent()]', {
      //     componentIdx: this.#componentIdx,
      //     waitable: this,
      //     waitableSet: this.#waitableSet,
      //     hasPendingEvent: this.#pendingEventFn !== null,
      // });
      return this.#pendingEventFn !== null;
    }
    
    setPendingEvent(fn) {
      _debugLog('[Waitable#setPendingEvent()] args', {
        waitable: this,
        inSet: this.#waitableSet,
      });
      this.#pendingEventFn = fn;
    }
    
    getPendingEvent() {
      _debugLog('[Waitable#getPendingEvent()] args', {
        waitable: this,
        inSet: this.#waitableSet,
        hasPendingEvent: this.#pendingEventFn !== null,
      });
      if (this.#pendingEventFn === null) { return null; }
      const eventFn = this.#pendingEventFn;
      this.#pendingEventFn = null;
      const e = eventFn();
      this.#resetPromise();
      return e;
    }
    
    join(waitableSet) {
      _debugLog('[Waitable#join()] args', {
        waitable: this,
        waitableSet: waitableSet,
        isRemoval: waitableSet === null,
      });
      
      if (this.#waitableSet === undefined) {
        throw new TypeError('waitable set must be not be undefined');
      }
      
      if (this.#waitableSet) {
        this.#waitableSet.removeWaitable(this);
      }
      
      this.#waitableSet = waitableSet;
      
      if (waitableSet) {
        this.#waitableSet.addWaitable(this);
      }
    }
    
    drop() {
      _debugLog('[Waitable#drop()] args', {
        componentIdx: this.#componentIdx,
        waitable: this,
      });
      if (this.hasPendingEvent()) {
        throw new Error('waitables with pending events cannot be dropped');
      }
      this.join(null);
    }
    
    async waitForPendingEvent(args) {
      const { cstate } = args;
      if (!cstate) { throw new TypeError('missing component state'); }
      
      if (this.#waitableSet !== null || this.#hasSyncWaiter) {
        throw new Error("waitable is already in a set/has a sync waiter");
      }
      this.#hasSyncWaiter = true;
      await cstate.waitUntil({
        cancellable: false,
        readyFn: () => this.hasPendingEvent(),
      });
      this.#hasSyncWaiter = false;
    }
    
  }
  const INSTANCE_FLAGS = new Map();
  const STORE_TRAP = { error: null };
  const STORE_ASYNC_STATE = { deadlockCheck: null, pendingHostOperations: 0 };
  
  function _checkForDeadlock() {
    if (STORE_ASYNC_STATE.deadlockCheck !== null || STORE_TRAP.error !== null) { return; }
    STORE_ASYNC_STATE.deadlockCheck = setTimeout(() => {
      STORE_ASYNC_STATE.deadlockCheck = null;
      if (STORE_TRAP.error !== null || STORE_ASYNC_STATE.pendingHostOperations > 0) { return; }
      
      const suspendedTasks = new Set();
      for (const state of ASYNC_STATE.values()) {
        if (state.hasPendingSchedulerWork()) {
          state.runTickLoop();
          return;
        }
        for (const meta of state.suspendedTaskMetas()) {
          suspendedTasks.add(meta.task);
        }
      }
      
      const unresolvedRoots = new Set();
      for (const task of suspendedTasks) {
        const root = task.getRootTask();
        if (!root.isResolvedState()) { unresolvedRoots.add(root); }
      }
      if (unresolvedRoots.size === 0) { return; }
      
      const err = new WebAssemblyRuntimeError('wasm trap: deadlock detected: event loop cannot make further progress');
      // Which tasks were waiting, and on whose behalf. The message stays
      // exactly what the Canonical ABI calls for, so this rides alongside
      // it: a deadlock reported from a real program is otherwise a bare
      // sentence, and the state that produced it is gone by the time
      // anyone reads the failure.
      err.deadlockDetail = {
        pendingHostOperations: STORE_ASYNC_STATE.pendingHostOperations,
        suspendedTasks: [...suspendedTasks].map((task) => ({
          taskID: task.id(),
          componentIdx: task.componentIdx(),
          state: task.taskState(),
          rootTaskID: task.getRootTask().id(),
        })),
        unresolvedRootTaskIDs: [...unresolvedRoots].map((root) => root.id()),
      };
      STORE_TRAP.error = err;
      for (const root of unresolvedRoots) {
        root.setErrored(err);
        root.reject(err);
      }
      for (const task of suspendedTasks) {
        if (!task.isResolvedState() && unresolvedRoots.has(task.getRootTask())) {
          task.setErrored(err);
          task.reject(err);
        }
      }
      for (const state of ASYNC_STATE.values()) { state.runTickLoop(); }
    }, 0);
  }
  
  const CORE_TRAP_MESSAGES = new Map([
  ['unreachable', "wasm trap: wasm `unreachable` instruction executed"],
  ['memory access out of bounds', "wasm trap: out of bounds memory access"],
  ['divide by zero', "wasm trap: integer divide by zero"],
  ['remainder by zero', "wasm trap: integer divide by zero"],
  ['divide result unrepresentable', "wasm trap: integer overflow"],
  ['float unrepresentable in integer range', "wasm trap: invalid conversion to integer"],
  ['table index is out of bounds', "wasm trap: undefined element: out of bounds table access"],
  ['function signature mismatch', "wasm trap: indirect call type mismatch"],
  ['call stack exhausted', "wasm trap: call stack exhausted"],
  ]);
  function _normalizeCoreTrap(err) {
    if (!(err instanceof WebAssemblyRuntimeError)) { return err; }
    const message = CORE_TRAP_MESSAGES.get(err.message);
    if (message !== undefined) { err.message = message; }
    return err;
  }
  
  class RepTable {
    // Sentinel marking a freed slot; the freelist link for a freed slot
    // lives in the odd cell. This keeps get()/contains()/remove() on freed
    // reps well-defined (previously they returned/corrupted freelist links).
    static FREE = Symbol('RepTable.free');
    
    #data = [0, null];
    #size = 0;
    #target;
    
    constructor(args) {
      this.target = args?.target;
    }
    
    data() { return this.#data; }
    
    insert(val) {
      _debugLog('[RepTable#insert()] args', { val, target: this.target });
      const freeIdx = this.#data[0];
      if (freeIdx === 0) {
        this.#data.push(val);
        this.#data.push(null);
        const rep = (this.#data.length >> 1) - 1;
        _debugLog('[RepTable#insert()] inserted', { val, target: this.target, rep });
        this.#size += 1;
        return rep;
      }
      const placementIdx = freeIdx << 1;
      if (this.#data[placementIdx] !== RepTable.FREE) {
        throw new Error('corrupt rep table freelist: head does not point at a freed slot');
      }
      this.#data[0] = this.#data[placementIdx + 1];
      this.#data[placementIdx] = val;
      this.#data[placementIdx + 1] = null;
      _debugLog('[RepTable#insert()] inserted', { val, target: this.target, rep: freeIdx });
      this.#size += 1;
      return freeIdx;
    }
    
    get(rep) {
      _debugLog('[RepTable#get()] args', { rep, target: this.target });
      if (rep === 0) { throw new Error('invalid resource rep during get, (cannot be 0)'); }
      
      const baseIdx = rep << 1;
      const val = this.#data[baseIdx];
      if (val === RepTable.FREE) { return undefined; }
      return val;
    }
    
    contains(rep) {
      _debugLog('[RepTable#contains()] args', { rep, target: this.target });
      if (rep === 0) { throw new Error('invalid resource rep during contains, (cannot be 0)'); }
      
      const baseIdx = rep << 1;
      const val = this.#data[baseIdx];
      return val !== RepTable.FREE && !!val;
    }
    
    remove(rep) {
      _debugLog('[RepTable#remove()] args', { rep, target: this.target });
      if (rep === 0) { throw new Error('invalid resource rep during remove, (cannot be 0)'); }
      if (this.#data.length === 2) { throw new Error('invalid'); }
      
      const baseIdx = rep << 1;
      if (baseIdx >= this.#data.length) {
        throw new Error(`invalid rep [${rep}] during remove, out of range`);
      }
      const val = this.#data[baseIdx];
      if (val === RepTable.FREE) {
        throw new Error(`double removal of rep [${rep}] (already freed)`);
      }
      
      this.#data[baseIdx] = RepTable.FREE;
      this.#data[baseIdx + 1] = this.#data[0];
      this.#data[0] = rep;
      this.#size -= 1;
      
      return val;
    }
    
    size() { return this.#size; }
    
    clear() {
      _debugLog('[RepTable#clear()] args', { rep, target: this.target });
      this.#data = [0, null];
    }
  }
  
  class ComponentAsyncState {
    static EVENT_HANDLER_EVENTS = [ 'backpressure-change' ];
    
    static TickResult = {
      // no suspended tasks remain
      DONE: 'done',
      // a suspended task was resumed (more may be ready)
      RESUMED: 'resumed',
      // suspended tasks remain but none were ready
      IDLE: 'idle',
    };
    
    #componentIdx;
    #callingAsyncImport = false;
    #syncImportWait = promiseWithResolvers();
    #lockHolderTaskID = null;
    #lockWaiters = [];
    #lockHandoffScheduled = false;
    #pendingTaskStarts = 0;
    #parkedTasks = new Map();
    #suspendedTasksByTaskID = new Map();
    #suspendedTaskIDs = [];
    #errored = null;
    #trapped = false;
    #backpressure = 0;
    #backpressureWaiters = 0n;
    
    #handlerMap = new Map();
    #nextHandlerID = 0n;
    
    #tickLoop = null;
    #tickLoopInterval = null;
    
    #onExclusiveReleaseHandlers = [];
    
    #mayLeave = true;
    
    handles;
    subtasks;
    
    constructor(args) {
      this.#componentIdx = args.componentIdx;
      this.handles = new RepTable({ target: `component [${this.#componentIdx}] handles (waitable objects)` });
      this.subtasks = new RepTable({ target: `component [${this.#componentIdx}] subtasks` });
    };
    
    componentIdx() { return this.#componentIdx; }
    
    get mayLeave() {
      const flags = INSTANCE_FLAGS.get(this.#componentIdx);
      return flags === undefined ? this.#mayLeave : flags.value === 1;
    }
    set mayLeave(value) {
      if (typeof value !== 'boolean') { throw new TypeError('mayLeave must be a boolean'); }
      this.#mayLeave = value;
      const flags = INSTANCE_FLAGS.get(this.#componentIdx);
      if (flags !== undefined) { flags.value = value ? 1 : 0; }
    }
    
    errored() { return this.#errored !== null; }
    setErrored(err) {
      _debugLog('[ComponentAsyncState#setErrored()] component errored', { err, componentIdx: this.#componentIdx });
      if (this.#errored) { return; }
      if (!err) {
        err = new Error('error elswehere (see other component instance error)')
        err.componentIdx = this.#componentIdx;
      }
      this.#errored = err;
    }
    
    markTrapped(err) {
      if (!(err instanceof WebAssemblyRuntimeError)) {
        return false;
      }
      err = _normalizeCoreTrap(err);
      this.#trapped = true;
      _debugLog('[ComponentAsyncState#markTrapped()] component trapped', { err, componentIdx: this.#componentIdx });
      if (STORE_TRAP.error === null) { STORE_TRAP.error = err; }
      return true;
    }
    
    throwIfTrapped() {
      if (this.#trapped) {
        throw new WebAssemblyRuntimeError("wasm trap: cannot enter component instance");
      }
    }
    
    callingSyncImport(val) {
      if (val === undefined) { return this.#callingAsyncImport; }
      if (typeof val !== 'boolean') { throw new TypeError('invalid setting for async import'); }
      const prev = this.#callingAsyncImport;
      this.#callingAsyncImport = val;
      if (prev === true && this.#callingAsyncImport === false) {
        this.#notifySyncImportEnd();
      }
    }
    
    #notifySyncImportEnd() {
      const existing = this.#syncImportWait;
      this.#syncImportWait = promiseWithResolvers();
      existing.resolve();
    }
    
    async waitForSyncImportCallEnd() {
      await this.#syncImportWait.promise;
    }
    
    setBackpressure(v) {
      this.#backpressure = v;
      return this.#backpressure
    }
    getBackpressure() { return this.#backpressure; }
    
    incrementBackpressure() {
      const current = this.#backpressure;
      if (current < 0 || current > 2**16) {
        throw new Error(`invalid current backpressure value [${current}]`);
      }
      const newValue = this.getBackpressure() + 1;
      if (newValue >= 2**16) {
        throw new Error(`invalid new backpressure value [${newValue}], overflow`);
      }
      return this.setBackpressure(newValue);
    }
    
    decrementBackpressure() {
      const current = this.#backpressure;
      if (current < 0 || current > 2**16) {
        throw new Error(`invalid current backpressure value [${current}]`);
      }
      const newValue = Math.max(0, current - 1);
      if (newValue < 0) {
        throw new Error(`invalid new backpressure value [${newValue}], underflow`);
      }
      return this.setBackpressure(newValue);
    }
    hasBackpressure() { return this.#backpressure > 0; }
    
    waitForBackpressure() {
      let backpressureCleared = false;
      const cstate = this;
      cstate.addBackpressureWaiter();
      const handlerID = this.registerHandler({
        event: 'backpressure-change',
        fn: (bp) => {
          if (bp === 0) {
            cstate.removeHandler(handlerID);
            backpressureCleared = true;
          }
        }
      });
      return new Promise((resolve) => {
        const interval = setInterval(() => {
          if (backpressureCleared) { return; }
          clearInterval(interval);
          cstate.removeBackpressureWaiter();
          resolve(null);
        }, 0);
      });
    }
    
    registerHandler(args) {
      const { event, fn } = args;
      if (!event) { throw new Error("missing handler event"); }
      if (!fn) { throw new Error("missing handler fn"); }
      
      if (!ComponentAsyncState.EVENT_HANDLER_EVENTS.includes(event)) {
        throw new Error(`unrecognized event handler [${event}]`);
      }
      
      const handlerID = this.#nextHandlerID++;
      let handlers = this.#handlerMap.get(event);
      if (!handlers) {
        handlers = [];
        this.#handlerMap.set(event, handlers)
      }
      
      handlers.push({ id: handlerID, fn, event });
      return handlerID;
    }
    
    removeHandler(args) {
      const { event, handlerID } = args;
      const registeredHandlers = this.#handlerMap.get(event);
      if (!registeredHandlers) { return; }
      const found = registeredHandlers.find(h => h.id === handlerID);
      if (!found) { return; }
      this.#handlerMap.set(event, this.#handlerMap.get(event).filter(h => h.id !== handlerID));
    }
    
    getBackpressureWaiters() { return this.#backpressureWaiters; }
    addBackpressureWaiter() { this.#backpressureWaiters++; }
    removeBackpressureWaiter() {
      this.#backpressureWaiters--;
      if (this.#backpressureWaiters < 0) {
        throw new Error("unexepctedly negative number of backpressure waiters");
      }
    }
    
    // The per-slice mutual-exclusion lock for guest execution in this
    // component instance. Guest slices (callback invocations and
    // sync-lifted bodies) must be atomic per component even across the
    // JSPI suspensions jco introduces for host imports: wit-bindgen's
    // executors publish per-task state in single linear-memory cells
    // (the wasip3-task pointer, context-local storage discipline) that
    // an interleaved slice of the same component corrupts
    //
    // The lock is *owned*: acquisition records the holder task and
    // release is a no-op for anyone else, so a task exiting can no
    // longer drop a hold it does not own (blind acquire/release-any
    // was the previous discipline). Contended acquisition queues
    // FIFO; release hands the lock to the next waiter directly.
    isExclusivelyLocked() { return this.#lockHolderTaskID !== null; }
    exclusivelyLockedBy(taskID) { return this.#lockHolderTaskID === taskID; }
    
    exclusiveLock(taskID) {
      _debugLog('[ComponentAsyncState#exclusiveLock()]', {
        holder: this.#lockHolderTaskID,
        requester: taskID,
        componentIdx: this.#componentIdx,
      });
      if (taskID === undefined || taskID === null) {
        throw new Error('exclusive lock requires the acquiring task id');
      }
      if (this.#lockHolderTaskID !== null) {
        throw new Error(`component [${this.#componentIdx}] exclusive lock held by task [${this.#lockHolderTaskID}], requested by [${taskID}]`);
      }
      this.#lockHolderTaskID = taskID;
    }
    
    // Awaitable acquisition: takes the lock immediately when free,
    // otherwise queues FIFO behind the current holder and earlier
    // waiters. The resolved promise implies ownership.
    acquireExclusiveLock(taskID) {
      if (taskID === undefined || taskID === null) {
        throw new Error('exclusive lock requires the acquiring task id');
      }
      if (this.#lockHolderTaskID === null) {
        this.#lockHolderTaskID = taskID;
        _debugLog('[ComponentAsyncState#acquireExclusiveLock()] acquired', {
          holder: taskID,
          componentIdx: this.#componentIdx,
        });
        return;
      }
      if (this.#lockHolderTaskID === taskID) {
        throw new Error(`task [${taskID}] already holds the lock for component [${this.#componentIdx}]`);
      }
      _debugLog('[ComponentAsyncState#acquireExclusiveLock()] waiting', {
        holder: this.#lockHolderTaskID,
        requester: taskID,
        componentIdx: this.#componentIdx,
        queued: this.#lockWaiters.length,
      });
      return new Promise((resolve) => {
        this.#lockWaiters.push({ taskID, resolve });
      });
    }
    
    exclusiveRelease(taskID) {
      _debugLog('[ComponentAsyncState#exclusiveRelease()] args', {
        holder: this.#lockHolderTaskID,
        releaser: taskID,
        componentIdx: this.#componentIdx,
      });
      if (this.#lockHolderTaskID !== taskID) {
        // Ownerless releases were the historical behavior; a foreign
        // release now leaves the hold intact
        _debugLog('[ComponentAsyncState#exclusiveRelease()] ignoring foreign release', {
          holder: this.#lockHolderTaskID,
          releaser: taskID,
          componentIdx: this.#componentIdx,
        });
        return false;
      }
      
      // Make the release observable before handing the lock to the next
      // asynchronous guest slice.
      //
      // Release handlers may expose a lifted value whose consumer immediately
      // performs a synchronous call on the same component; that call must run
      // while the instance is genuinely unlocked, not via enterSync's
      // lock-free fallback code.
      this.#lockHolderTaskID = null;
      
      this.#onExclusiveReleaseHandlers = this.#onExclusiveReleaseHandlers.filter(v => !!v);
      for (const [idx, f] of this.#onExclusiveReleaseHandlers.entries()) {
        try {
          this.#onExclusiveReleaseHandlers[idx] = null;
          f();
        } catch (err) {
          _debugLog("error while executing handler for next exclusive release", err);
          throw err;
        }
      }
      this.#scheduleLockHandoff();
      return true;
    }
    
    #scheduleLockHandoff() {
      if (this.#lockHandoffScheduled || this.#lockWaiters.length === 0) { return; }
      this.#lockHandoffScheduled = true;
      queueMicrotask(() => {
        this.#lockHandoffScheduled = false;
        // A synchronous call triggered by a release handler gets the
        // first opportunity to use the unlocked component.
        //
        // Its release will leave this queued handoff in place.
        if (this.#lockHolderTaskID !== null) {
          this.#scheduleLockHandoff();
          return;
        }
        const next = this.#lockWaiters.shift();
        if (!next) { return; }
        this.#lockHolderTaskID = next.taskID;
        next.resolve();
      });
    }
    
    onNextExclusiveRelease(fn) {
      _debugLog('[ComponentAsyncState#()onNextExclusiveRelease] registering');
      this.#onExclusiveReleaseHandlers.push(fn);
    }
    
    async waitForExclusiveRelease() {
      while (this.isExclusivelyLocked()) {
        await new Promise(resolve => this.onNextExclusiveRelease(resolve));
      }
    }
    
    #getSuspendedTaskMeta(taskID) {
      return this.#suspendedTasksByTaskID.get(taskID);
    }
    
    #removeSuspendedTaskMeta(taskID) {
      _debugLog('[ComponentAsyncState#removeSuspendedTaskMeta()] removing suspended task', {
        taskID,
        componentIdx: this.#componentIdx,
      });
      const idx = this.#suspendedTaskIDs.findIndex(t => t === taskID);
      const meta = this.#suspendedTasksByTaskID.get(taskID);
      this.#suspendedTaskIDs[idx] = null;
      this.#suspendedTasksByTaskID.delete(taskID);
      return meta;
    }
    
    #addSuspendedTaskMeta(meta) {
      if (!meta) { throw new Error('missing task meta'); }
      const taskID = meta.taskID;
      this.#suspendedTasksByTaskID.set(taskID, meta);
      this.#suspendedTaskIDs.push(taskID);
      if (this.#suspendedTasksByTaskID.size < this.#suspendedTaskIDs.length - 10) {
        this.#suspendedTaskIDs = this.#suspendedTaskIDs.filter(t => t !== null);
      }
    }
    
    // TODO(threads): readyFn is normally on the thread
    suspendTask(args) {
      const { task, readyFn, cancellable, onResume } = args;
      const taskID = task.id();
      const componentIdx = task.componentIdx();
      _debugLog('[ComponentAsyncState#suspendTask()]', {
        taskID,
        componentIdx: this.#componentIdx,
        taskEntryFnName: task.entryFnName(),
        subtask: task.getParentSubtask(),
      });
      
      if (componentIdx !== this.#componentIdx) {
        throw new Error('assert: task component idx should match async state');
      }
      
      if (this.#getSuspendedTaskMeta(taskID)) {
        throw new Error(`task [${taskID}] already suspended`);
      }
      
      let promise;
      let resume;
      if (onResume) {
        resume = () => onResume(!task.isCancelled());
      } else {
        const resolvers = promiseWithResolvers();
        promise = resolvers.promise;
        resume = () => resolvers.resolve(!task.isCancelled());
      }
      this.#addSuspendedTaskMeta({
        task,
        taskID,
        cancellable,
        readyFn,
        resume: () => {
          _debugLog('[ComponentAsyncState] resuming suspended task', {
            taskID,
            componentIdx: this.#componentIdx,
          });
          // TODO(threads): it's thread cancellation we should be checking for below, not task
          resume();
        },
      });
      
      // A caller synchronously driving one task quantum (for
      // example, subtask.cancel) waits for the resumed task to
      // either resolve or suspend again.
      task.notifyProgress();
      
      this.runTickLoop();
      _checkForDeadlock();
      
      return promise;
    }
    
    resumeTaskByID(taskID) {
      const meta = this.#removeSuspendedTaskMeta(taskID);
      if (!meta) { return false; }
      if (meta.taskID !== taskID) { throw new Error('task ID does not match'); }
      meta.resume();
      return true;
    }
    
    suspendedTaskReady(taskID) {
      const meta = this.#getSuspendedTaskMeta(taskID);
      if (!meta) { return false; }
      if (!meta.readyFn) {
        throw new Error(`suspended task [${taskID}] is missing a readiness function`);
      }
      return meta.task.isRejected() || meta.readyFn();
    }
    
    suspendedTaskCancellable(taskID) {
      return !!this.#getSuspendedTaskMeta(taskID)?.cancellable;
    }
    
    isTaskSuspended(taskID) {
      return this.#suspendedTasksByTaskID.has(taskID);
    }
    
    suspendedTaskMetas() {
      return this.#suspendedTasksByTaskID.values();
    }
    
    addPendingTaskStart() { this.#pendingTaskStarts++; }
    removePendingTaskStart() { this.#pendingTaskStarts--; }
    
    hasPendingSchedulerWork() {
      if (this.#pendingTaskStarts > 0) { return true; }
      if (this.#lockHandoffScheduled) { return true; }
      for (const meta of this.#suspendedTasksByTaskID.values()) {
        if (meta.task.isRejected() || meta.readyFn()) { return true; }
      }
      return false;
    }
    
    async runTickLoop() {
      if (this.#tickLoop !== null) { return; }
      this.#tickLoop = 1;
      setTimeout(async () => {
        let result = this.tick();
        while (result !== ComponentAsyncState.TickResult.DONE) {
          if (result === ComponentAsyncState.TickResult.IDLE) {
            _checkForDeadlock();
          }
          // After resuming a task, re-tick as soon as the resumed
          // slice's microtask continuations have drained (timeout 0)
          // so queued sibling resumptions aren't charged the idle
          // polling interval; otherwise poll at the idle cadence.
          const delay = result === ComponentAsyncState.TickResult.RESUMED ? 0 : 10;
          await new Promise((resolve) => setTimeout(resolve, delay));
          result = this.tick();
        }
        this.#tickLoop = null;
      }, 10);
    }
    
    tick() {
      // _debugLog('[ComponentAsyncState#tick()]', { suspendedTaskIDs: this.#suspendedTaskIDs });
      
      const resumableTasks = this.#suspendedTaskIDs.filter(t => t !== null);
      for (const taskID of resumableTasks) {
        const meta = this.#suspendedTasksByTaskID.get(taskID);
        if (!meta || !meta.readyFn) {
          throw new Error(`missing/invalid task despite ID [${taskID}] being present`);
        }
        
        // If the task failed via any means, allow the task to resume because
        // it's been cancelled -- the callback should immediately exit as well
        if (meta.task.isRejected()) {
          _debugLog('[ComponentAsyncState#tick()] detected task rejection, leaving early', { meta });
          this.resumeTaskByID(taskID);
          return ComponentAsyncState.TickResult.RESUMED;
        }
        
        const isReady = meta.readyFn();
        if (!isReady) { continue; }
        
        _debugLog('[ComponentAsyncState#tick()] resuming task via tick', {
          taskID,
          componentIdx: this.#componentIdx,
        });
        this.resumeTaskByID(taskID);
        
        // NOTE: during single-flight resumption, we should resume at most one task per
        // tick so that the resumed slice (a microtask continuation)
        // runs -- and its current-task register window opens and
        // closes -- before any sibling task of this component is
        // resumed.
        //
        // Resuming multiple suspended tasks in one synchronous
        // cascade interleaves their register save/restore windows
        // ([restoreA, restoreB, resumeA, resumeB]), re-entering wasm
        // with the register naming the wrong task, and the
        // 'known residual' of the JSPI current-task register
        // fix); with concurrent task lifetimes per component this
        // corrupts guest context-local storage.
        return ComponentAsyncState.TickResult.RESUMED;
      }
      
      const idle = this.#suspendedTaskIDs.filter(t => t !== null).length > 0;
      return idle
      ? ComponentAsyncState.TickResult.IDLE
      : ComponentAsyncState.TickResult.DONE;
    }
    
    createWaitable(args) {
      return new Waitable({ target: args?.target, });
    }
  }
  
  function getOrCreateAsyncState(componentIdx, init) {
    if (!ASYNC_STATE.has(componentIdx)) {
      const newState = new ComponentAsyncState({ componentIdx });
      ASYNC_STATE.set(componentIdx, newState);
    }
    return ASYNC_STATE.get(componentIdx);
  }
  const GLOBAL_COMPONENT_MEMORY_MAP = new Map();
  
  function lookupMemoriesForComponent(args) {
    const { componentIdx } = args ?? {};
    if (args.componentIdx === undefined) { throw new TypeError("missing component idx"); }
    
    const metas = GLOBAL_COMPONENT_MEMORY_MAP.get(componentIdx);
    if (!metas) { return []; }
    
    if (args.memoryIdx === undefined) {
      return Object.values(metas);
    }
    
    const meta = metas[args.memoryIdx];
    return meta?.memory;
  }
  
  class AsyncSubtask {
    static _ID = 0n;
    
    static State = {
      STARTING: 0,
      STARTED: 1,
      RETURNED: 2,
      CANCELLED_BEFORE_STARTED: 3,
      CANCELLED_BEFORE_RETURNED: 4,
    };
    
    #id;
    #state = AsyncSubtask.State.STARTING;
    #componentIdx;
    
    #parentTask;
    #childTask = null;
    
    #dropped = false;
    #cancelRequested = false;
    
    #memoryIdx = null;
    #lenders = null;
    
    #waitable = null;
    
    #callbackFn = null;
    #callbackFnName = null;
    
    #postReturnFn = null;
    #onProgressFn = null;
    #pendingEventFn = null;
    
    #callMetadata = {};
    
    #resolved = false;
    
    #onResolveHandlers = [];
    #onStartHandlers = [];
    
    #result = null;
    #resultSet = false;
    
    fnName;
    target;
    isAsync;
    isManualAsync;
    // One execution slice awaited by the conditional cancel trampoline.
    cancelProgress = null;
    
    constructor(args) {
      if (typeof args.componentIdx !== 'number') {
        throw new Error('invalid componentIdx for subtask creation');
      }
      this.#componentIdx = args.componentIdx;
      
      this.#id = ++AsyncSubtask._ID;
      this.fnName = args.fnName;
      
      if (!args.parentTask) { throw new Error('missing parent task during subtask creation'); }
      this.#parentTask = args.parentTask;
      
      if (args.childTask) { this.#childTask = args.childTask; }
      
      if (args.memoryIdx) { this.#memoryIdx = args.memoryIdx; }
      
      if (!args.waitable) { throw new Error("missing/invalid waitable"); }
      this.#waitable = args.waitable;
      
      if (args.callMetadata) { this.#callMetadata = args.callMetadata; }
      
      this.#lenders = [];
      this.target = args.target;
      this.isAsync = args.isAsync;
      this.isManualAsync = args.isManualAsync;
    }
    
    id() { return this.#id; }
    parentTaskID() { return this.#parentTask?.id(); }
    childTaskID() { return this.#childTask?.id(); }
    state() { return this.#state; }
    
    waitable() { return this.#waitable; }
    waitableRep() { return this.#waitable.idx(); }
    
    join() { return this.#waitable.join(...arguments); }
    getPendingEvent() { return this.#waitable.getPendingEvent(...arguments); }
    hasPendingEvent() { return this.#waitable.hasPendingEvent(...arguments); }
    setPendingEvent() { return this.#waitable.setPendingEvent(...arguments); }
    
    setTarget(tgt) { this.target = tgt; }
    
    getResult() {
      if (!this.#resultSet) { throw new Error("subtask result has not been set") }
      return this.#result;
    }
    setResult(v) {
      if (this.#resultSet) { throw new Error("subtask result has already been set"); }
      this.#result = v;
      this.#resultSet = true;
    }
    
    componentIdx() { return this.#componentIdx; }
    
    setChildTask(t) {
      if (!t) { throw new Error('cannot set missing/invalid child task on subtask'); }
      if (this.#childTask) { throw new Error('child task is already set on subtask'); }
      if (this.#parentTask === t) { throw new Error("parent cannot be child"); }
      this.#childTask = t;
    }
    getChildTask(t) { return this.#childTask; }
    
    getParentTask() { return this.#parentTask; }
    
    setCallbackFn(f, name) {
      if (!f) { return; }
      if (this.#callbackFn) { throw new Error('callback fn can only be set once'); }
      this.#callbackFn = f;
      this.#callbackFnName = name;
    }
    
    getCallbackFnName() {
      if (!this.#callbackFn) { return undefined; }
      return this.#callbackFn.name;
    }
    
    setPostReturnFn(f) {
      if (!f) { return; }
      if (this.#postReturnFn) { throw new Error('postReturn fn can only be set once'); }
      this.#postReturnFn = f;
    }
    
    setOnProgressFn(f) {
      if (this.#onProgressFn) { throw new Error('on progress fn can only be set once'); }
      this.#onProgressFn = f;
    }
    
    isNotStarted() {
      return this.#state == AsyncSubtask.State.STARTING;
    }
    
    cancellationRequested() { return this.#cancelRequested; }
    
    // Request cooperative cancellation of this subtask, on behalf of the
    // supertask (i.e. `canon subtask.cancel`).
    //
    // If the callee is another guest task, the request is delivered to it and
    // the callee confirms via `task.cancel` (or still resolves via `task.return`).
    //
    // If the callee is a host function there is (currently) no host-side
    // cancellation hook, so the pending call is treated as immediately
    // cancelled -- consistent with hosts being expected to resolve
    // cancellation promptly -- and any later host resolution is discarded
    // (see `AsyncTask#onResolve`).
    requestCancellation() {
      _debugLog('[AsyncSubtask#requestCancellation()] args', {
        componentIdx: this.#componentIdx,
        subtaskID: this.#id,
        state: this.#state,
        childTaskID: this.childTaskID(),
        fnName: this.fnName,
      });
      if (this.#cancelRequested) {
        throw new Error('cancellation has already been requested for this subtask');
      }
      this.#cancelRequested = true;
      
      if (this.#resolved) { return; }
      
      if (this.#childTask) {
        this.#childTask.requestCancellation();
        return;
      }
      
      this.onResolve(null);
    }
    
    registerOnStartHandler(f) {
      this.#onStartHandlers.push(f);
    }
    
    onStart(args) {
      _debugLog('[AsyncSubtask#onStart()] args', {
        componentIdx: this.#componentIdx,
        subtaskID: this.#id,
        parentTaskID: this.parentTaskID(),
        fnName: this.fnName,
        args,
      });
      
      if (this.#onProgressFn) { this.#onProgressFn(); }
      
      // Starting a nested operation is a task execution boundary.
      // In particular, a cancellation handler may synchronously
      // enter an import which then suspends in the host.  Wake a
      // supertask that is driving one cancellation slice so an
      // async `subtask.cancel` can report BLOCKED without waiting
      // for that nested operation to finish.
      this.#parentTask.notifyProgress();
      
      this.#state = AsyncSubtask.State.STARTED;
      
      let result;
      
      // If we have been provided a helper start function as a result of
      // component fusion performed by wasmtime tooling, then we can call that helper and lifts/lowers will
      // be performed for us.
      //
      // See also documentation on `HostIntrinsic::PrepareCall`
      //
      if (this.#callMetadata.startFn) {
        result = this.#callMetadata.startFn.apply(null, args?.startFnParams ?? []);
      }
      
      return result;
    }
    
    
    registerOnResolveHandler(f) {
      this.#onResolveHandlers.push(f);
    }
    
    reject(subtaskErr) {
      if (this.#resolved) { return; }
      
      if (this.#onProgressFn) { this.#onProgressFn(); }
      
      if (this.#state === AsyncSubtask.State.STARTING) {
        this.#state = AsyncSubtask.State.CANCELLED_BEFORE_STARTED;
      } else if (this.#state === AsyncSubtask.State.STARTED) {
        this.#state = AsyncSubtask.State.CANCELLED_BEFORE_RETURNED;
      } else {
        throw new Error('cannot reject a completed subtask');
      }
      
      this.#resolved = true;
      this.#parentTask.removeSubtask(this);
      this.#parentTask.reject(subtaskErr);
    }
    
    onResolve(subtaskValue) {
      _debugLog('[AsyncSubtask#onResolve()] args', {
        componentIdx: this.#componentIdx,
        subtaskID: this.#id,
        isAsync: this.isAsync,
        childTaskID: this.childTaskID(),
        parentTaskID: this.parentTaskID(),
        parentTaskFnName: this.#parentTask?.entryFnName(),
        fnName: this.fnName,
      });
      
      if (this.#resolved) {
        throw new Error('subtask has already been resolved');
      }
      
      if (this.#onProgressFn) { this.#onProgressFn(); }
      
      if (subtaskValue === null && this.#cancelRequested) {
        if (this.#state === AsyncSubtask.State.STARTING) {
          this.#state = AsyncSubtask.State.CANCELLED_BEFORE_STARTED;
        } else {
          if (this.#state !== AsyncSubtask.State.STARTED) {
            throw new Error('resolved subtask must have been started before cancellation');
          }
          this.#state = AsyncSubtask.State.CANCELLED_BEFORE_RETURNED;
        }
      } else {
        if (this.#state !== AsyncSubtask.State.STARTED) {
          throw new Error('resolved subtask must have been started before completion');
        }
        this.#state = AsyncSubtask.State.RETURNED;
      }
      
      this.setResult(subtaskValue);
      
      for (const f of this.#onResolveHandlers) {
        try {
          f(subtaskValue);
        } catch (err) {
          console.error("error during subtask resolve handler", err);
          throw err;
        }
      }
      
      const callMetadata = this.getCallMetadata();
      
      // TODO(fix): we should be able to easily have the caller's meomry
      // to lower into here, but it's not present in PrepareCall
      const memory = callMetadata.memory ?? this.#parentTask?.getReturnMemory() ?? lookupMemoriesForComponent({ componentIdx: this.#parentTask?.componentIdx() })[0];
      // NOTE: cancelled resolutions carry no value, so nothing is lowered
      const returned = this.#state === AsyncSubtask.State.RETURNED;
      if (returned && callMetadata && !callMetadata.returnFn && (this.isAsync || callMetadata.funcTypeIsAsync) && callMetadata.resultPtr && memory) {
        const { resultPtr, realloc } = callMetadata;
        const lowers = callMetadata.lowers; // may have been updated in task.return of the child
        if (lowers && lowers.length > 0) {
          lowers[0]({
            componentIdx: this.#componentIdx,
            memory,
            realloc,
            vals: [subtaskValue],
            storagePtr: resultPtr,
            stringEncoding: callMetadata.stringEncoding,
          });
        }
      }
      
      this.#resolved = true;
      this.#parentTask.removeSubtask(this);
      
      if (!this.isAsync) {
        this.deliverResolve();
        const rep = this.waitableRep();
        if (rep) {
          try {
            const removed = this.#getComponentState().handles.remove(rep);
            if (removed !== this) {
              throw new Error("unexpectedly received non-self Subtask from handle removal");
            }
            this.drop();
          } catch (err) {
            _debugLog('[AsyncSubtask#onResolve()] failed to remove subtask after sync subtask completion', err);
          }
        }
      }
    }
    
    getStateNumber() { return this.#state; }
    isReturned() { return this.#state === AsyncSubtask.State.RETURNED; }
    
    getCallMetadata() { return this.#callMetadata; }
    
    isResolved() {
      if (this.#state === AsyncSubtask.State.STARTING
      || this.#state === AsyncSubtask.State.STARTED) {
        return false;
      }
      if (this.#state === AsyncSubtask.State.RETURNED
      || this.#state === AsyncSubtask.State.CANCELLED_BEFORE_STARTED
      || this.#state === AsyncSubtask.State.CANCELLED_BEFORE_RETURNED) {
        return true;
      }
      throw new Error('unrecognized internal Subtask state [' + this.#state + ']');
    }
    
    addLender(handle) {
      _debugLog('[AsyncSubtask#addLender()] args', { handle });
      if (!Number.isNumber(handle)) { throw new Error('missing/invalid lender handle [' + handle + ']'); }
      
      if (this.#lenders.length === 0 || this.isResolved()) {
        throw new Error('subtask has no lendors or has already been resolved');
      }
      
      handle.lends++;
      this.#lenders.push(handle);
    }
    
    deliverResolve() {
      _debugLog('[AsyncSubtask#deliverResolve()] args', {
        lenders: this.#lenders,
        parentTaskID: this.parentTaskID(),
        subtaskID: this.#id,
        childTaskID: this.childTaskID(),
        resolved: this.isResolved(),
        resolveDelivered: this.resolveDelivered(),
      });
      
      const cannotDeliverResolve = this.resolveDelivered() || !this.isResolved();
      if (cannotDeliverResolve) {
        throw new Error('subtask cannot deliver resolution twice, and the subtask must be resolved');
      }
      
      for (const lender of this.#lenders) {
        lender.lends--;
      }
      
      this.#lenders = null;
    }
    
    resolveDelivered() {
      _debugLog('[AsyncSubtask#resolveDelivered()] args', { });
      if (this.#lenders === null && !this.isResolved()) {
        throw new Error('invalid subtask state, lenders missing and subtask has not been resolved');
      }
      return this.#lenders === null;
    }
    
    drop() {
      _debugLog('[AsyncSubtask#drop()] args', {
        componentIdx: this.#componentIdx,
        parentTaskID: this.#parentTask?.id(),
        parentTaskFnName: this.#parentTask?.entryFnName(),
        childTaskID: this.#childTask?.id(),
        childTaskFnName: this.#childTask?.entryFnName(),
        subtaskFnName: this.fnName,
      });
      if (!this.#waitable) { throw new Error('missing/invalid inner waitable'); }
      if (!this.resolveDelivered()) {
        throw new Error('cannot drop a subtask which has not yet resolved');
      }
      if (this.#waitable) { this.#waitable.drop() }
      this.#dropped = true;
    }
    
    #getComponentState() {
      const state = getOrCreateAsyncState(this.#componentIdx);
      if (!state) {
        throw new Error('invalid/missing async state for component [' + componentIdx + ']');
      }
      return state;
    }
    
    getWaitableHandleIdx() {
      _debugLog('[AsyncSubtask#getWaitableHandleIdx()] args', { });
      if (!this.#waitable) { throw new Error('missing/invalid waitable'); }
      return this.waitableRep();
    }
  }
  
  class FutureValue {
    #start;
    #settled;
    #hideThen = 0;
    #thenFn;
    
    constructor(start) {
      if (typeof start !== 'function') {
        throw new TypeError('future start operation must be a function');
      }
      this.#start = start;
      this.#thenFn = this.#then.bind(this);
    }
    
    get then() {
      return this.#hideThen === 0 ? this.#thenFn : undefined;
    }
    
    #read() {
      if (!this.#settled) {
        // The start operation resolves to a non-thenable box so a
        // future-valued payload cannot be assimilated by this Promise.
        this.#settled = Promise.resolve().then(this.#start);
      }
      return this.#settled;
    }
    
    resolveAsValue(resolve) {
      this.#hideThen++;
      try {
        resolve(this);
      } finally {
        this.#hideThen--;
      }
    }
    
    #deliver(resolve, value) {
      if (value instanceof FutureValue) {
        // Promise resolution reads `then` synchronously. Hide it only
        // for that lookup so resolving this layer yields the inner
        // FutureValue instead of recursively awaiting it.
        value.resolveAsValue(resolve);
        return;
      }
      resolve(value);
    }
    
    #then(resolve, reject) {
      return this.#read().then(
      box => this.#deliver(resolve, box.value),
      reject,
      );
    }
  }
  const ASYNC_DETERMINISM = 'random';
  const _coinFlip = () => { return Math.random() > 0.5; };
  
  const ASYNC_EVENT_CODE = {
    NONE: 0,
    SUBTASK: 1,
    STREAM_READ: 2,
    STREAM_WRITE: 3,
    FUTURE_READ: 4,
    FUTURE_WRITE: 5,
    TASK_CANCELLED: 6,
  };
  const CURRENT_TASK_META = {};
  
  function _withGlobalCurrentTaskMeta(args) {
    _debugLog('[_withGlobalCurrentTaskMeta()] args', args);
    if (!args) { throw new TypeError('args missing'); }
    if (args.taskID === undefined) { throw new TypeError('missing task ID'); }
    if (args.componentIdx === undefined) { throw new TypeError('missing component idx'); }
    if (!args.fn) { throw new TypeError('missing fn'); }
    const { taskID, componentIdx, fn } = args;
    const previous = CURRENT_TASK_META[componentIdx] ?? null;
    const previousCurrent = CURRENT_TASK_META.current ?? null;
    
    try {
      CURRENT_TASK_META.current =
      CURRENT_TASK_META[componentIdx] = { taskID, componentIdx };
      return fn();
    } catch (err) {
      _debugLog("error while executing sync callee/callback", {
        ...args,
        err,
      });
      throw err;
    } finally {
      // Synchronous wrappers can nest without any intervening JS
      // scheduling. Restore the caller rather than clearing it so
      // helper core exports (for example fused return adapters) can
      // temporarily run under a different task of the same component.
      CURRENT_TASK_META[componentIdx] = previous;
      CURRENT_TASK_META.current = previousCurrent;
    }
  }
  
  async function _withGlobalCurrentTaskMetaAsync(args) {
    _debugLog('[_withGlobalCurrentTaskMetaAsync()] args', args);
    if (!args) { throw new TypeError('args missing'); }
    if (args.taskID === undefined) { throw new TypeError('missing task ID'); }
    if (args.componentIdx === undefined) { throw new TypeError('missing component idx'); }
    if (!args.fn) { throw new TypeError('missing fn'); }
    
    const { taskID, componentIdx, fn } = args;
    
    try {
      CURRENT_TASK_META.current =
      CURRENT_TASK_META[componentIdx] = { taskID, componentIdx };
      return await fn();
    } catch (err) {
      _debugLog("error while executing async callee/callback", {
        ...args,
        err,
      });
      throw err;
    } finally {
      CURRENT_TASK_META[componentIdx] = null;
      if (CURRENT_TASK_META.current?.taskID === taskID) {
        CURRENT_TASK_META.current = null;
      }
    }
  }
  
  class AsyncTask {
    static _ID = 0n;
    
    static State = {
      INITIAL: 'initial',
      CANCELLED: 'cancelled',
      CANCEL_PENDING: 'cancel-pending',
      CANCEL_DELIVERED: 'cancel-delivered',
      RESOLVED: 'resolved',
    }
    
    static BlockResult = {
      CANCELLED: 'block.cancelled',
      NOT_CANCELLED: 'block.not-cancelled',
    }
    
    #id;
    #componentIdx;
    #state;
    #isAsync;
    #isManualAsync;
    #callingWasmExport = true;
    #lockFreeEntry = false;
    #preserveFutureResult;
    #entryFnName = null;
    
    #onResolveHandlers = [];
    #progressWaiters = [];
    #completionPromise = null;
    #completionValue;
    #completionReady = false;
    #settleCompletionPromise;
    #rejected = false;
    
    #exitPromise = null;
    #onExitHandlers = [];
    
    #memoryIdx = null;
    #memory = null;
    
    #callbackFn = null;
    #callbackFnName = null;
    
    #postReturnFn = null;
    
    #getCalleeParamsFn = null;
    #calleeIsAsync = null;
    
    #stringEncoding = null;
    
    #parentSubtask = null;
    
    #errHandling;
    
    #backpressurePromise;
    #backpressureWaiters = 0n;
    
    #returnLowerFns = null;
    
    #resourceScopeId;
    #resourceBorrowCount = 0;
    #resourceLenders = [];
    #resourceScopeExited = false;
    
    #subtasks = [];
    
    #entered = false;
    #exited = false;
    #errored = null;
    
    cancelled = false;
    cancelRequested = false;
    alwaysTaskReturn = false;
    
    returnCalls =  0;
    storage = [0, 0];
    tmpRetI64HighBits = 0|0;
    
    constructor(opts) {
      this.#id = ++AsyncTask._ID;
      this.#resourceScopeId = ++RESOURCE_SCOPE_ID;
      RESOURCE_SCOPE_TASKS.set(this.#resourceScopeId, this);
      
      if (opts?.componentIdx === undefined) {
        throw new TypeError('missing component id during task creation');
      }
      this.#componentIdx = opts.componentIdx;
      
      this.#state = AsyncTask.State.INITIAL;
      this.#isAsync = opts?.isAsync ?? false;
      this.#isManualAsync = opts?.isManualAsync ?? false;
      this.#preserveFutureResult = opts?.preserveFutureResult ?? false;
      this.#entryFnName = opts.entryFnName;
      // Tasks that execute guest slices (export calls, fused
      // callees) default to true; import-handler tasks pass false
      // explicitly (they run host code nested inside the caller's
      // already-locked slice).
      this.#callingWasmExport = opts?.callingWasmExport !== false;
      
      const {
        promise: completionPromise,
        resolve: resolveCompletionPromise,
        reject: rejectCompletionPromise,
      } = promiseWithResolvers();
      this.#completionPromise = completionPromise;
      // A nested rejection can reach the root task while its Wasm
      // entrypoint is still suspended, before the export wrapper awaits
      // this promise. Mark it handled immediately while preserving the
      // original rejected promise for the eventual caller.
      completionPromise.catch(() => {});
      
      let completionSettled = false;
      const settleCompletionPromise = () => {
        if (completionSettled || !this.#completionReady) { return; }
        completionSettled = true;
        if (this.#errored !== null) {
          rejectCompletionPromise(this.#errored);
        } else if (this.#rejected) {
          rejectCompletionPromise(this.#completionValue);
        } else if (
        this.#preserveFutureResult
        && this.#completionValue instanceof FutureValue
        ) {
          this.#completionValue.resolveAsValue(resolveCompletionPromise);
        } else {
          resolveCompletionPromise(this.#completionValue);
        }
      };
      
      this.#settleCompletionPromise = settleCompletionPromise;
      this.#onResolveHandlers.push((results) => {
        if (this.#parentSubtask !== null) { return; }
        if (!this.#isAsync && !this.#isManualAsync) { return; }
        this.#completionValue = results;
        this.#completionReady = true;
        // Publish after the current guest slice returns, so a trap
        // in that slice can still reject the call. Do not wait for
        // task exit: detached work may require further host calls
        // or consumption of a returned resource's stream.
      });
      
      const {
        promise: exitPromise,
        resolve: resolveExitPromise,
        reject: rejectExitPromise,
      } = promiseWithResolvers();
      this.#exitPromise = exitPromise;
      
      this.#onExitHandlers.push(() => {
        if (this.#parentSubtask === null && (this.#isAsync || this.#isManualAsync)) {
          settleCompletionPromise();
        }
        resolveExitPromise();
      });
      
      if (opts.callbackFn) { this.#callbackFn = opts.callbackFn; }
      if (opts.callbackFnName) { this.#callbackFnName = opts.callbackFnName; }
      
      if (opts.getCalleeParamsFn) { this.#getCalleeParamsFn = opts.getCalleeParamsFn; }
      if (opts.stringEncoding) { this.#stringEncoding = opts.stringEncoding; }
      
      if (opts.parentSubtask) { this.#parentSubtask = opts.parentSubtask; }
      
      
      if (opts.errHandling) { this.#errHandling = opts.errHandling; }
    }
    
    taskState() { return this.#state; }
    id() { return this.#id; }
    componentIdx() { return this.#componentIdx; }
    entryFnName() { return this.#entryFnName; }
    
    resourceScopeId() { return this.#resourceScopeId; }
    
    addBorrowedHandle() {
      if (this.#resourceScopeExited) {
        throw new Error('cannot add a borrow to an exited resource scope');
      }
      this.#resourceBorrowCount++;
    }
    
    removeBorrowedHandle() {
      if (this.#resourceBorrowCount === 0) {
        throw new Error('resource borrow count underflow');
      }
      this.#resourceBorrowCount--;
    }
    
    addResourceLender(table, handle) {
      if (this.#resourceScopeExited) {
        throw new Error('cannot add a lender to an exited resource scope');
      }
      this.#resourceLenders.push({ table, handle });
    }
    
    validateResourceBorrowScope() {
      if (this.#resourceScopeExited) { return; }
      if (this.#resourceBorrowCount !== 0) {
        throw new WebAssemblyRuntimeError('borrow handles still remain at the end of the call');
      }
      for (const { table, handle } of this.#resourceLenders) {
        const idx = handle << 1;
        const lendCount = table[idx];
        if (!Number.isInteger(lendCount) || lendCount <= 0 || lendCount >= 2**30) {
          throw new Error('invalid resource lender state at scope exit');
        }
        table[idx] = lendCount - 1;
      }
      this.#resourceLenders = [];
      this.#resourceScopeExited = true;
      RESOURCE_SCOPE_TASKS.delete(this.#resourceScopeId);
    }
    
    completionPromise() { return this.#completionPromise; }
    settleCompletion() { this.#settleCompletionPromise(); }
    exitPromise() { return this.#exitPromise; }
    
    waitForProgress() {
      const { promise, resolve } = promiseWithResolvers();
      this.#progressWaiters.push(resolve);
      return promise;
    }
    
    notifyProgress() {
      const waiters = this.#progressWaiters;
      this.#progressWaiters = [];
      for (const resolve of waiters) { resolve(); }
    }
    
    isAsync() { return this.#isAsync; }
    isManualAsync() { return this.#isManualAsync; }
    isSync() { return !this.isAsync(); }
    
    getErrHandling() { return this.#errHandling; }
    
    hasCallback() { return this.#callbackFn !== null; }
    
    getReturnMemoryIdx() { return this.#memoryIdx; }
    setReturnMemoryIdx(idx) {
      if (idx === null) { return; }
      this.#memoryIdx = idx;
    }
    
    getReturnMemory() { return this.#memory; }
    setReturnMemory(m) {
      if (m === null) { return; }
      this.#memory = m;
    }
    
    setReturnLowerFns(fns) { this.#returnLowerFns = fns; }
    getReturnLowerFns() { return this.#returnLowerFns; }
    
    setCalleeIsAsync(value) {
      if (typeof value !== 'boolean') { throw new TypeError('callee async state must be a boolean'); }
      this.#calleeIsAsync = value;
    }
    
    setParentSubtask(subtask) {
      if (!subtask || !(subtask instanceof AsyncSubtask)) { return }
      if (this.#parentSubtask) { throw new Error('parent subtask can only be set once'); }
      this.#parentSubtask = subtask;
    }
    
    getParentSubtask() { return this.#parentSubtask; }
    
    // TODO(threads): this is very inefficient, we can pass along a root task,
    // and ideally do not need this once thread support is in place
    getRootTask() {
      let currentSubtask = this.getParentSubtask();
      let task = this;
      while (currentSubtask) {
        task = currentSubtask.getParentTask();
        currentSubtask = task.getParentSubtask();
      }
      return task;
    }
    
    setPostReturnFn(f) {
      if (!f) { return; }
      if (this.#postReturnFn) { throw new Error('postReturn fn can only be set once'); }
      this.#postReturnFn = f;
    }
    
    setCallbackFn(f, name) {
      if (!f) { return; }
      if (this.#callbackFn) { throw new Error('callback fn can only be set once'); }
      this.#callbackFn = f;
      this.#callbackFnName = name;
    }
    
    getCallbackFnName() {
      if (!this.#callbackFnName) { return undefined; }
      return this.#callbackFnName;
    }
    
    runCallbackFn(...args) {
      if (!this.#callbackFn) { throw new Error('no callback function has been set for task'); }
      if (this.#callbackFn._jcoMaySuspend === false) {
        return _withGlobalCurrentTaskMeta({
          taskID: this.#id,
          componentIdx: this.#componentIdx,
          fn: () => this.#callbackFn.apply(null, args),
        });
      }
      return _withGlobalCurrentTaskMetaAsync({
        taskID: this.#id,
        componentIdx: this.#componentIdx,
        fn: () => { return this.#callbackFn.apply(null, args); }
      });
    }
    
    getCalleeParams() {
      if (!this.#getCalleeParamsFn) { throw new Error('missing/invalid getCalleeParamsFn'); }
      return this.#getCalleeParamsFn();
    }
    
    // Legacy manually-async exports are sync-typed in the component
    // but use JSPI precisely so their guest stack may suspend.
    mayBlock() { return this.isAsync() || this.isManualAsync() || this.isResolvedState() }
    
    mayEnter(task) {
      const cstate = getOrCreateAsyncState(this.#componentIdx);
      if (cstate.hasBackpressure()) {
        _debugLog('[AsyncTask#mayEnter()] disallowed due to backpressure', { taskID: this.#id });
        return false;
      }
      if (!cstate.callingSyncImport()) {
        _debugLog('[AsyncTask#mayEnter()] disallowed due to sync import call', { taskID: this.#id });
        return false;
      }
      const callingSyncExportWithSyncPending = cstate.callingSyncExport && !task.isAsync;
      if (!callingSyncExportWithSyncPending) {
        _debugLog('[AsyncTask#mayEnter()] disallowed due to sync export w/ sync pending', { taskID: this.#id });
        return false;
      }
      return true;
    }
    
    enterSync() {
      if (this.needsExclusiveLock()) {
        const cstate = getOrCreateAsyncState(this.#componentIdx);
        if (!cstate.isExclusivelyLocked()) {
          cstate.exclusiveLock(this.#id);
        } else {
          // A host-called sync export arriving while another
          // task's slice holds the lock: synchronous entry
          // cannot wait, and historically this entry silently
          // stole the hold. Run without the lock instead --
          // the holder's bookkeeping stays intact and its
          // release still pairs
          this.#lockFreeEntry = true;
          _debugLog('[AsyncTask#enterSync()] entering without exclusive lock', {
            taskID: this.#id,
            componentIdx: this.#componentIdx,
          });
        }
      }
      return true;
    }
    
    tryEnter() {
      if (this.#entered) {
        throw new Error(`task with ID [${this.#id}] should not be entered twice`);
      }
      
      if (this.deliverPendingCancel({ cancellable: true })) {
        this.cancel();
        return false;
      }
      
      const cstate = getOrCreateAsyncState(this.#componentIdx);
      if (this.isSync()) {
        this.#entered = true;
        return true;
      }
      if (cstate.hasBackpressure()) { return null; }
      if (this.needsExclusiveLock()) {
        if (cstate.isExclusivelyLocked()) { return null; }
        cstate.exclusiveLock(this.#id);
      }
      
      if (this.deliverPendingCancel({ cancellable: true })) {
        cstate.exclusiveRelease(this.#id);
        this.cancel();
        return false;
      }
      
      this.#entered = true;
      return true;
    }
    
    async enter(opts) {
      _debugLog('[AsyncTask#enter()] args', {
        taskID: this.#id,
        componentIdx: this.#componentIdx,
        subtaskID: this.getParentSubtask()?.id(),
        args: opts,
        entryFnName: this.#entryFnName,
      });
      
      if (this.#entered) {
        throw new Error(`task with ID [${this.#id}] should not be entered twice`);
      }
      
      // If cancellation was requested before the task was entered, resolve
      // as cancelled without ever running guest code
      if (this.deliverPendingCancel({ cancellable: true })) {
        this.cancel();
        return false;
      }
      
      const cstate = getOrCreateAsyncState(this.#componentIdx);
      
      if (opts?.isHost) {
        this.#entered = true;
        // A guest task may be synchronously blocked in this
        // host entry. Propagate that boundary so an async
        // cancellation driver can yield BLOCKED while the host
        // operation is outstanding.
        const parentTask = this.#parentSubtask?.getParentTask();
        if (
        parentTask?.taskState() === AsyncTask.State.CANCEL_DELIVERED
        || (parentTask && !parentTask.hasCallback())
        ) {
          parentTask.notifyProgress();
        }
        return this.#entered;
      }
      
      // NOTE: concurrent task lifetimes within one component instance are
      // permitted by the Component Model: entry is governed by the
      // backpressure and exclusive-lock checks below (the lock is held per
      // execution slice, not for the task's lifetime).
      //
      // Serializing entire task lifetimes here (the former "execution slot" queue)
      // deadlocks pipelines where a parked long-lived task's progress depends on a
      // later entry into the same component.
      
      // If a task is synchronous then we can avoid component-relevant
      // tracking and immediately enter.
      if (this.isSync()) {
        this.#entered = true;
        
        // TODO(breaking): remove once manually-specifying async fns is removed
        // It is currently possible for an actually sync export to be specified
        // as async via JSPI
        if (this.#isManualAsync) {
          if (this.needsExclusiveLock()) { await cstate.acquireExclusiveLock(this.#id); }
        }
        
        return this.#entered;
      }
      
      // Perform intial backpressure check
      if (cstate.hasBackpressure()) {
        cstate.addBackpressureWaiter();
        
        const result = await this.waitUntil({
          readyFn: () => {
            return !cstate.hasBackpressure();
          },
          cancellable: true,
        });
        
        cstate.removeBackpressureWaiter();
        
        if (!result || this.isCancelled()) {
          if (!this.isResolvedState()) { this.cancel(); }
          return false;
        }
      }
      
      // Acquire the per-slice exclusive lock (FIFO-queued when
      // contended); the first slice runs under this hold and the
      // driver loop releases/re-acquires it per slice thereafter.
      if (this.needsExclusiveLock()) {
        await cstate.acquireExclusiveLock(this.#id);
      }
      
      // Cancellation-before-start may resolve this task while its
      // queued lock acquisition is still pending. Acquiring the lock
      // does not make the already-resolved task runnable again.
      if (this.isResolvedState() || this.isCancelled()) {
        cstate.exclusiveRelease(this.#id);
        return false;
      }
      
      // Cancellation can be requested while entry is waiting for
      // backpressure or its exclusive lock. Do not execute the guest
      // after acquiring a lock for a task that should no longer start.
      if (this.deliverPendingCancel({ cancellable: true })) {
        cstate.exclusiveRelease(this.#id);
        this.cancel();
        return false;
      }
      
      this.#entered = true;
      return this.#entered;
    }
    
    isRunningState() { return this.#state !== AsyncTask.State.RESOLVED; }
    isResolvedState() { return this.#state === AsyncTask.State.RESOLVED; }
    isResolved() { return this.#state === AsyncTask.State.RESOLVED; }
    isExited() { return this.#exited; }
    
    async waitUntil(opts) {
      const { readyFn, cancellable } = opts;
      _debugLog('[AsyncTask#waitUntil()] args', { taskID: this.#id, args: { cancellable } });
      
      // TODO(fix): check for cancel
      // TODO(fix): determinism
      // TODO(threads): add this thread to waiting list
      
      const keepGoing = await this.suspendUntil({
        readyFn,
        cancellable,
      });
      
      return keepGoing;
    }
    
    async yieldUntil(opts) {
      const { readyFn, cancellable } = opts;
      _debugLog('[AsyncTask#yieldUntil()]', {
        taskID: this.#id,
        args: {
          cancellable,
        },
        componentIdx: this.#componentIdx,
      });
      
      // A callback YIELD is an explicit request to let other work run.
      // Unlike waits whose condition is already ready, it must always
      // suspend for at least one scheduler turn.
      const keepGoing = await this.immediateSuspend({ readyFn, cancellable });
      if (keepGoing) {
        return {
          code: ASYNC_EVENT_CODE.NONE,
          payload0: 0,
          payload1: 0,
        };
      }
      
      return {
        code: ASYNC_EVENT_CODE.TASK_CANCELLED,
        payload0: 0,
        payload1: 0,
      };
    }
    
    async suspendUntil(opts) {
      const { cancellable, readyFn } = opts;
      _debugLog('[AsyncTask#suspendUntil()] args', {
        taskID: this.#id,
        args: {
          cancellable,
        },
        componentIdx: this.#componentIdx,
      });
      
      const pendingCancelled = this.deliverPendingCancel({ cancellable });
      if (pendingCancelled) { return false; }
      
      const completed = await this.immediateSuspendUntil({ readyFn, cancellable });
      return completed;
    }
    
    suspendUntilCallback(opts, onResume) {
      const { cancellable, readyFn } = opts;
      if (this.deliverPendingCancel({ cancellable })) {
        onResume(false);
        return;
      }
      
      const cstate = getOrCreateAsyncState(this.#componentIdx);
      cstate.suspendTask({
        task: this,
        cancellable,
        readyFn: () => {
          if (cancellable && this.#state === AsyncTask.State.CANCEL_PENDING) {
            return true;
          }
          return readyFn();
        },
        onResume: (keepGoing) => {
          if (keepGoing && this.deliverPendingCancel({ cancellable })) {
            keepGoing = false;
          }
          onResume(keepGoing);
        },
      });
    }
    
    // TODO(threads): equivalent to thread.suspend_until()
    async immediateSuspendUntil(opts) {
      const { cancellable, readyFn } = opts;
      _debugLog('[AsyncTask#immediateSuspendUntil()] args', {
        args: {
          cancellable,
          readyFn,
        },
        taskID: this.#id,
        componentIdx: this.#componentIdx,
      });
      
      const ready = readyFn();
      if (ready && ASYNC_DETERMINISM === 'random') {
        const coinFlip = _coinFlip();
        if (coinFlip) { return true }
      }
      
      const keepGoing = await this.immediateSuspend({ cancellable, readyFn });
      return keepGoing;
    }
    
    async immediateSuspend(opts) { // NOTE: equivalent to thread.suspend()
    // TODO(threads): store readyFn on the thread
    const { cancellable, readyFn } = opts;
    _debugLog('[AsyncTask#immediateSuspend()] args', { cancellable, readyFn });
    
    const pendingCancelled = this.deliverPendingCancel({ cancellable });
    if (pendingCancelled) { return false; }
    
    const cstate = getOrCreateAsyncState(this.#componentIdx);
    const keepGoing = await cstate.suspendTask({
      task: this,
      cancellable,
      readyFn: () => {
        // A pending cancellation request wakes cancellable waits
        if (cancellable && this.#state === AsyncTask.State.CANCEL_PENDING) {
          return true;
        }
        return readyFn();
      },
    });
    if (keepGoing && this.deliverPendingCancel({ cancellable })) { return false; }
    return keepGoing;
  }
  
  deliverPendingCancel(opts) {
    const { cancellable } = opts;
    _debugLog('[AsyncTask#deliverPendingCancel()]', {
      args: { cancellable },
      taskID: this.#id,
      componentIdx: this.#componentIdx,
    });
    
    if (cancellable && this.#state === AsyncTask.State.CANCEL_PENDING) {
      this.#state = AsyncTask.State.CANCEL_DELIVERED;
      return true;
    }
    
    return false;
  }
  
  isCancelled() { return this.cancelled }
  cancellationRequested() { return this.cancelRequested; }
  
  // Request cooperative cancellation of this task, called on behalf of a
  // supertask performing `subtask.cancel` on the subtask this task backs.
  //
  // The request is delivered at this task's next cancellable wait
  // (see suspendUntil/immediateSuspend), at which point the task is
  // expected to acknowledge via `task.cancel` or still resolve via
  // `task.return`.
  requestCancellation() {
    _debugLog('[AsyncTask#requestCancellation()] args', {
      taskID: this.#id,
      componentIdx: this.#componentIdx,
      state: this.#state,
    });
    if (this.isResolvedState() || this.cancelRequested) { return; }
    this.cancelRequested = true;
    if (this.#state === AsyncTask.State.INITIAL) {
      this.#state = AsyncTask.State.CANCEL_PENDING;
    }
    // Nudge the component's tick loop so that any suspended cancellable
    // wait observes the pending cancellation promptly
    getOrCreateAsyncState(this.#componentIdx).runTickLoop();
  }
  
  cancel(args) {
    _debugLog('[AsyncTask#cancel()] args', { });
    if (this.taskState() !== AsyncTask.State.CANCEL_DELIVERED) {
      throw new Error(`(component [${this.#componentIdx}]) task [${this.#id}] invalid task state [${this.taskState()}] for cancellation`);
    }
    this.validateResourceBorrowScope();
    this.cancelled = true;
    // Cancelled tasks resolve with no value (spec: `Task.cancel` calls
    // `on_resolve(None)`); an explicit error is only present on the
    // host-driven rejection path (see `reject()`).
    this.onResolve(args?.error ?? null);
    this.#state = AsyncTask.State.RESOLVED;
    // A task cancelled before entry has no driver loop that can
    // report its exit. Entered tasks notify after releasing their
    // component slice in `exit()`.
    if (!this.#entered) { this.notifyProgress(); }
  }
  
  onResolve(taskValue) {
    const handlers = this.#onResolveHandlers;
    this.#onResolveHandlers = [];
    for (const f of handlers) {
      try {
        f(taskValue);
      } catch (err) {
        _debugLog("[AsyncTask#onResolve] error during task resolve handler", err);
        throw err;
      }
    }
    
    // Rejections are control-flow failures, not canonical ABI results.
    // Propagate them through the subtask chain without running return
    // lowering or post-return hooks for a successful result.
    if (this.#rejected) {
      this.#parentSubtask?.reject(taskValue);
      return;
    }
    
    // NOTE: if the parent subtask has already been resolved (e.g. it was
    // cancelled via `subtask.cancel` while this task was still pending),
    // this task's resolution must be discarded rather than delivered.
    const parentSubtaskPending = this.#parentSubtask && !this.#parentSubtask.isResolved();
    const taskReturned = !this.isCancelled();
    
    if (parentSubtaskPending && taskReturned) {
      const meta = this.#parentSubtask.getCallMetadata();
      // Run the rturn fn if it has not already been called -- this *should* have happened in
      // `task.return`, but some paths do not go through task.return (e.g. async lower of sync fn
      // which goes through prepare + async-start-call)
      if (meta.returnFn && !meta.returnFnCalled) {
        _debugLog('[AsyncTask#onResolve()] running returnFn', {
          componentIdx: this.#componentIdx,
          taskID: this.#id,
          subtaskID: this.#parentSubtask.id(),
        });
        const callerTask = this.#parentSubtask.getParentTask();
        _withGlobalCurrentTaskMeta({
          taskID: callerTask.id(),
          componentIdx: callerTask.componentIdx(),
          fn: () => meta.returnFn.apply(null, [taskValue, meta.resultPtr]),
        });
        meta.returnFnCalled = true;
      }
    }
    
    if (this.#postReturnFn && taskReturned) {
      _debugLog('[AsyncTask#onResolve()] running post return ', {
        componentIdx: this.#componentIdx,
        taskID: this.#id,
      });
      try {
        _withGlobalCurrentTaskMeta({
          taskID: this.#id,
          componentIdx: this.#componentIdx,
          fn: () => this.#postReturnFn(taskValue),
        });
      } catch (err) {
        _debugLog("[AsyncTask#onResolve] error during task resolve handler", err);
        throw err;
      }
    }
    
    if (parentSubtaskPending) {
      this.#parentSubtask.onResolve(taskValue);
    }
  }
  
  registerOnResolveHandler(f) {
    this.#onResolveHandlers.push(f);
  }
  
  isRejected() { return this.#rejected; }
  
  isErrored() { return this.#errored; }
  setErrored(err) {
    // Preserve the originating trap when unwinding through
    // additional guest frames produces secondary traps (often
    // an `unreachable` after a call which was expected to trap).
    if (this.#errored === null) { this.#errored = err; }
  }
  
  reject(taskErr) {
    _debugLog('[AsyncTask#reject()] args', {
      componentIdx: this.#componentIdx,
      taskID: this.#id,
      parentSubtask: this.#parentSubtask,
      parentSubtaskID: this.#parentSubtask?.id(),
      entryFnName: this.entryFnName(),
      callbackFnName: this.#callbackFnName,
      errMsg: taskErr.message,
    });
    
    this.setErrored(taskErr);
    if (this.#rejected) { return; }
    
    // A task may call `task.return` before its callback exits.
    // A trap in cleanup or spawned work must still reject the
    // host call and poison the enclosing task chain.
    if (this.isResolvedState()) {
      this.#rejected = true;
      this.#errored = taskErr;
      const parentTask = this.#parentSubtask?.getParentTask();
      if (parentTask) { parentTask.reject(taskErr); }
      return;
    }
    
    this.#rejected = true;
    this.cancelRequested = true;
    this.#state = AsyncTask.State.CANCEL_PENDING;
    const cancelled = this.deliverPendingCancel({ cancellable: true });
    
    // TODO: do cleanup here to reset the machinery so we can run again?
    
    this.cancel({ error: taskErr });
  }
  
  resolve(results) {
    _debugLog('[AsyncTask#resolve()] args', {
      componentIdx: this.#componentIdx,
      taskID: this.#id,
      entryFnName: this.entryFnName(),
      callbackFnName: this.#callbackFnName,
    });
    
    if (this.#state === AsyncTask.State.RESOLVED) {
      throw new Error(`(component [${this.#componentIdx}]) task [${this.#id}]  is already resolved (did you forget to wait for an import?)`);
    }
    
    this.validateResourceBorrowScope();
    
    this.#state = AsyncTask.State.RESOLVED;
    
    switch (results.length) {
      case 0:
      this.onResolve(undefined);
      break;
      case 1:
      this.onResolve(results[0]);
      break;
      default:
      _debugLog('[AsyncTask#resolve()] unexpected number of results', {
        componentIdx: this.#componentIdx,
        results,
        taskID: this.#id,
        subtaskID: this.#parentSubtask?.id(),
        entryFnName: this.#entryFnName,
        callbackFnName: this.#callbackFnName,
      });
      throw new Error('unexpected number of results');
    }
  }
  
  exit(args) {
    _debugLog('[AsyncTask#exit()]', {
      componentIdx: this.#componentIdx,
      taskID: this.#id,
    });
    
    if (this.#exited)  { throw new Error("task has already exited"); }
    
    if (this.#state !== AsyncTask.State.RESOLVED) {
      throw new Error(`(component [${this.#componentIdx}]) task [${this.#id}] exited without resolution`);
    }
    
    this.validateResourceBorrowScope();
    
    const state = getOrCreateAsyncState(this.#componentIdx);
    if (!state) { throw new Error('missing async state for component [' + this.#componentIdx + ']'); }
    
    // Exempt the host from exclusive lock check
    if (this.#componentIdx !== -1 && !args?.skipExclusiveLockCheck && !this.#lockFreeEntry) {
      if (this.needsExclusiveLock() && !state.exclusivelyLockedBy(this.#id)) {
        throw new Error(`task [${this.#id}] exit: component [${this.#componentIdx}] should have been exclusively locked by it`);
      }
    }
    
    // Ownership-checked: releases only this task's own hold (a
    // task exiting while another task's slice holds the lock no
    // longer clears the foreign hold).
    state.exclusiveRelease(this.#id);
    this.notifyProgress();
    
    for (const f of this.#onExitHandlers) {
      try {
        f();
      } catch (err) {
        console.error("error during task exit handler", err);
        throw err;
      }
    }
    
    this.#exited = true;
    clearCurrentTask(this.#componentIdx, this.id());
  }
  
  needsExclusiveLock() {
    // Host (-1) tasks model host-side import handling: there is no
    // guest linear memory or executor state to protect, and host
    // calls from unrelated guest components would contend spuriously.
    if (this.#componentIdx === -1) { return false; }
    // Import-handler tasks (CallInterface) run host code nested
    // inside the calling guest slice, which already holds the
    // lock; only tasks that execute guest slices need it.
    if (!this.#callingWasmExport) { return false; }
    // A sync-lifted callee cannot be reentered until the whole
    // stackful call returns. This is the Component Model's
    // automatic-backpressure rule for async-lowered calls.
    return !this.#isAsync || this.hasCallback() || this.#calleeIsAsync === false;
  }
  
  createSubtask(args) {
    _debugLog('[AsyncTask#createSubtask()] args', args);
    const { componentIdx, childTask, callMetadata, fnName, isAsync, isManualAsync } = args;
    
    const cstate = getOrCreateAsyncState(this.#componentIdx);
    if (!cstate) {
      throw new Error(`invalid/missing async state for component idx [${componentIdx}]`);
    }
    
    const waitable = new Waitable({
      componentIdx: this.#componentIdx,
      target: `subtask (internal ID [${this.#id}])`,
    });
    
    const newSubtask = new AsyncSubtask({
      componentIdx,
      childTask,
      parentTask: this,
      callMetadata,
      isAsync,
      isManualAsync,
      fnName,
      waitable,
    });
    this.#subtasks.push(newSubtask);
    newSubtask.setTarget(`subtask (internal ID [${newSubtask.id()}], waitable [${waitable.idx()}], component [${componentIdx}])`);
    waitable.setIdx(cstate.handles.insert(newSubtask));
    waitable.setTarget(`waitable for subtask (waitable id [${waitable.idx()}], subtask internal ID [${newSubtask.id()}])`);
    return newSubtask;
  }
  
  getLatestSubtask() {
    return this.#subtasks.at(-1);
  }
  
  getSubtaskByWaitableRep(rep) {
    if (rep === undefined) { throw new TypeError('missing rep'); }
    return this.#subtasks.find(s => s.waitableRep() === rep);
  }
  
  currentSubtask() {
    _debugLog('[AsyncTask#currentSubtask()]');
    if (this.#subtasks.length === 0) { return undefined; }
    return this.#subtasks.at(-1);
  }
  
  removeSubtask(subtask) {
    if (this.#subtasks.length === 0) {
      throw new Error('cannot end current subtask: no current subtask');
    }
    this.#subtasks = this.#subtasks.filter(t => t !== subtask);
    return subtask;
  }
}

function createNewCurrentTask(args) {
  _debugLog('[createNewCurrentTask()] args', args);
  const {
    componentIdx,
    isAsync,
    isManualAsync,
    preserveFutureResult,
    entryFnName,
    parentSubtaskID,
    callbackFnName,
    getCallbackFn,
    getParamsFn,
    stringEncoding,
    errHandling,
    getCalleeParamsFn,
    resultPtr,
    callingWasmExport,
  } = args;
  if (componentIdx === undefined || componentIdx === null) {
    throw new Error('missing/invalid component instance index while starting task');
  }
  let taskMetas = ASYNC_TASKS_BY_COMPONENT_IDX.get(componentIdx);
  const callbackFn = getCallbackFn ? getCallbackFn() : null;
  
  const newTask = new AsyncTask({
    componentIdx,
    isAsync,
    isManualAsync,
    preserveFutureResult,
    entryFnName,
    callbackFn,
    callbackFnName,
    stringEncoding,
    getCalleeParamsFn,
    resultPtr,
    errHandling,
    callingWasmExport,
  });
  
  const newTaskID = newTask.id();
  const newTaskMeta = { id: newTaskID, componentIdx, task: newTask };
  
  // NOTE: do not track host tasks
  ASYNC_CURRENT_TASK_IDS.push(newTaskID);
  ASYNC_CURRENT_COMPONENT_IDXS.push(componentIdx);
  
  if (!taskMetas) {
    taskMetas = [newTaskMeta];
    ASYNC_TASKS_BY_COMPONENT_IDX.set(componentIdx, [newTaskMeta]);
  } else {
    taskMetas.push(newTaskMeta);
  }
  
  return [newTask, newTaskID];
}

function _checkMayLeave(componentIdx) {
  if (INSTANCE_FLAGS.get(componentIdx)?.value !== 1) {
    throw new WebAssemblyRuntimeError('cannot leave component instance');
  }
}

function _getGlobalCurrentTaskMeta(componentIdx) {
  const v = componentIdx === undefined || componentIdx === null
  ? CURRENT_TASK_META.current
  : CURRENT_TASK_META[componentIdx];
  if (v === undefined || v === null) {
    return undefined;
  }
  return { ...v };
}


function _setGlobalCurrentTaskMeta(args) {
  if (!args) { throw new TypeError('args missing'); }
  if (args.taskID === undefined) { throw new TypeError('missing task ID'); }
  if (args.componentIdx === undefined) { throw new TypeError('missing component idx'); }
  const { taskID, componentIdx } = args;
  return CURRENT_TASK_META.current =
  CURRENT_TASK_META[componentIdx] = { taskID, componentIdx };
}


async function _clearCurrentTask(args) {
  _debugLog('[_clearCurrentTask()] args', args);
  if (!args) { throw new TypeError('args missing'); }
  if (args.taskID === undefined) { throw new TypeError('missing task ID'); }
  if (args.componentIdx === undefined) { throw new TypeError('missing component idx'); }
  const { taskID, componentIdx } = args;
  
  const meta = CURRENT_TASK_META[componentIdx];
  if (!meta) { throw new Error(`missing current task meta for component idx [${componentIdx}]`); }
  
  if (meta.taskID !== taskID) {
    throw new Error(`task ID [${meta.taskID}] != requested ID [${taskID}]`);
  }
  if (meta.componentIdx !== componentIdx) {
    throw new Error(`component idx [${meta.componentIdx}] != requested idx [${componentIdx}]`);
  }
  
  CURRENT_TASK_META[componentIdx] = null;
  if (CURRENT_TASK_META.current?.taskID === taskID) {
    CURRENT_TASK_META.current = null;
  }
}

function _lowerImportBackwardsCompat(args) {
  const params = [...arguments].slice(1);
  _debugLog('[_lowerImportBackwardsCompat()] args', { args, params });
  const {
    functionIdx,
    componentIdx,
    isAsync,
    isManualAsync,
    paramLiftFns,
    resultLowerFns,
    hasResultPointer,
    funcTypeIsAsync,
    metadata,
    memoryIdx,
    getMemoryFn,
    getReallocFn,
    importFn,
    stringEncoding,
  } = args;
  
  _checkMayLeave(componentIdx);
  
  let meta = _getGlobalCurrentTaskMeta(componentIdx);
  let createdTask;
  
  // Some components depend on initialization logic (i.e. `_initialize` or some such
  // core wasm export) that is embedded in the component, but is not executed or wizer'd
  // away before the transpiled component is attempted to be used.
  //
  // These components execut their initialization logic *when they are imported* in the
  // transpiled context -- so we may get a call to an export that is lowered without going
  // through `CallWasm` or `CallInterface`.
  //
  if (!meta) {
    if (funcTypeIsAsync || (isAsync && !isManualAsync)) {
      throw new Error('p3 async wasm exports cannot use backwards compat auto-task init');
    }
    
    const [newTask, newTaskID] = createNewCurrentTask({
      componentIdx,
      isAsync,
      isManualAsync,
      callingWasmExport: false,
    });
    createdTask = newTask;
    
    // Since we're managing the task creation ourselves we must clear ourselves
    createdTask.registerOnResolveHandler(() => {
      _clearCurrentTask({
        taskID: task.id(),
        componentIdx: task.componentIdx(),
      });
    });
    
    _setGlobalCurrentTaskMeta({
      componentIdx,
      taskID: newTaskID,
    });
    
    meta = _getGlobalCurrentTaskMeta(componentIdx);
  }
  
  const { taskID } = meta;
  
  const taskMeta = getCurrentTask(componentIdx, taskID);
  if (!taskMeta) {
    throw new Error('invalid/missing async task meta');
  }
  
  const task = taskMeta.task;
  if (!task) { throw new Error('invalid/missing async task'); }
  
  const cstate = getOrCreateAsyncState(componentIdx);
  
  if (!task.mayBlock() && funcTypeIsAsync && !isAsync) {
    throw new Error("non async exports cannot synchronously call async functions");
  }
  
  // If there is an existing task, this should be part of a subtask
  const memory = getMemoryFn();
  // Canonical ABI lower appends result storage as a trailing
  // param when async lower has any flat result, or sync lower
  // has more than one flat result.
  const resultPtr = hasResultPointer ? params[params.length - 1] : undefined;
  const subtask = task.createSubtask({
    componentIdx,
    parentTask: task,
    fnName: importFn.fnName,
    isAsync,
    isManualAsync,
    callMetadata: {
      memoryIdx,
      memory,
      realloc: getReallocFn?.(),
      getReallocFn,
      resultPtr,
      lowers: resultLowerFns,
      funcTypeIsAsync,
      stringEncoding,
    }
  });
  task.setReturnMemoryIdx(memoryIdx);
  task.setReturnMemory(getMemoryFn());
  
  subtask.onStart();
  
  // If dealing with a sync lowered sync function, we can directly return results
  //
  // TODO(breaking): remove once we get rid of manual async import specification,
  // as func types cannot be detected in that case only (and we don't need that w/ p3)
  if (!isManualAsync && !isAsync && !funcTypeIsAsync) {
    if (createdTask) { createdTask.enterSync(); }
    
    const res = importFn(...params);
    
    // TODO(breaking): remove once we get rid of manual async import specification,
    // as func types cannot be detected in that case only (and we don't need that w/ p3)
    if (!funcTypeIsAsync && !subtask.isReturned()) {
      throw new Error('post-execution subtasks must either be async or returned');
    }
    
    const syncRes = subtask.getResult();
    if (createdTask) { createdTask.resolve([syncRes]); }
    
    return syncRes;
  }
  
  // Sync-lowered async functions requires async behavior because the callee *can* block,
  // but this call must *act* synchronously and return immediately with the result
  // (i.e. not returning until the work is done)
  //
  // TODO(breaking): remove checking for manual async specification here, once we can go p3-only
  //
  if (!isManualAsync && !isAsync && funcTypeIsAsync) {
    const { promise, resolve, reject } = promiseWithResolvers();
    queueMicrotask(async () => {
      try {
        await importFn(...params);
        if (!subtask.isResolved()) {
          await task.suspendUntil({ readyFn: () => subtask.isResolved() });
        }
        resolve(subtask.getResult());
      } catch (err) {
        reject(err);
      }
    });
    return promise;
  }
  
  // NOTE: at this point we know that we are working with an async lowered import
  
  const subtaskState = subtask.getStateNumber();
  if (subtaskState < 0 || subtaskState >= 2**4) {
    throw new Error('invalid subtask state, out of valid range');
  }
  
  subtask.setOnProgressFn(() => {
    subtask.setPendingEvent(() => {
      if (subtask.isResolved()) { subtask.deliverResolve(); }
      const event = {
        code: ASYNC_EVENT_CODE.SUBTASK,
        payload0: subtask.waitableRep(),
        payload1: subtask.getStateNumber(),
      }
      return event;
    });
  });
  
  // This is a hack to maintain backwards compatibility with
  // manually-specified async imports, used in wasm exports that are
  // not actually async (but are specified as so).
  //
  // This is not normal p3 sync behavior but instead anticipating that
  // the caller that is doing manual async will be waiting for a promise that
  // resolves to the *actual* result.
  //
  // TODO(breaking): remove once manually specified async is removed
  //
  // There are a few cases:
  // 1. sync function with async types (e.g. `f: func() -> stream<u32>`)
  // 2. async function with async types (e.g. `f: async func() -> stream<u32>`)
  // 3. async function with sync types (e.g. `f: async func() -> list<u32>`)
  // 4. sync function with non-async types (e.g. `f: func() -> list<u32>`)
  //
  // This hack *only* applies to 4 -- the case where an async JS host function
  // is supplied to a Wasm export which does *not* need to do any async abi
  // lifting/lowering (async ABI did not exist when JSPI integratiton was
  // initially merged to enable asynchronously returning values from the host)
  //
  const requiresManualAsyncResult = !isAsync && !funcTypeIsAsync && isManualAsync;
  let manualAsyncResult;
  if (requiresManualAsyncResult) {
    manualAsyncResult = promiseWithResolvers();
  }
  
  queueMicrotask(async () => {
    try {
      _debugLog('[_lowerImportBackwardsCompat()] calling lowered import', { importFn, params });
      if (createdTask) { await createdTask.enter(); }
      
      const asyncRes = await importFn(...params);
      if (requiresManualAsyncResult) {
        manualAsyncResult.resolve(subtask.getResult());
      }
      
      if (createdTask) { createdTask.resolve([asyncRes]); }
      
      
    } catch (err) {
      _debugLog("[_lowerImportBackwardsCompat()] import fn error:", err);
      if (requiresManualAsyncResult) {
        manualAsyncResult.reject(err);
        return;
      }
      task.setErrored(err);
      task.reject(err);
    }
  });
  
  if (requiresManualAsyncResult) { return manualAsyncResult.promise; }
  
  _debugLog('[_lowerImportBackwardsCompat()] async-lowered import return', {
    fnName: importFn.fnName,
    componentIdx,
    subtaskID: subtask.id(),
    waitableRep: subtask.waitableRep(),
    subtaskState,
    packedResult: Number(subtask.waitableRep()) << 4 | subtaskState,
  });
  
  return Number(subtask.waitableRep()) << 4 | subtaskState;
}

const CURRENT_TASK_MAY_BLOCK= globalThis.WebAssembly ? new globalThis.WebAssembly.Global({ value: 'i32', mutable: true }, 0) : false;


function _liftFlatU64(ctx) {
  _debugLog('[_liftFlatU64()] args', { ctx });
  let val;
  
  if (ctx.useDirectParams) {
    if (ctx.params.length === 0) { throw new Error('expected at least one single i64 argument'); }
    if (typeof ctx.params[0] !== 'bigint') { throw new Error('expected bigint'); }
    // core i64 values arrive as signed BigInts
    val = BigInt.asUintN(64, ctx.params[0]);
    ctx.params = ctx.params.slice(1);
    return [val, ctx];
  }
  
  if (ctx.storageLen !== undefined && ctx.storageLen < 8) {
    throw new Error(`insufficient storage ([${ctx.storageLen}] bytes) for lift (u64 requires 8 bytes)`);
  }
  
  val = new DataView(ctx.memory.buffer).getBigUint64(ctx.storagePtr, true);
  ctx.storagePtr += 8;
  if (ctx.storageLen !== undefined) { ctx.storageLen -= 8; }
  
  return [val, ctx];
}


function _lowerFlatU8(ctx) {
  _debugLog('[_lowerFlatU8()] args', ctx);
  
  if (ctx.vals.length !== 1) {
    throw new Error(`unexpected number [${ctx.vals.length}] of vals (expected 1)`);
  }
  
  _requireValidNumericPrimitive.bind('u8', ctx.vals[0]);
  
  if (!ctx.memory) { throw new Error("missing memory for lower"); }
  new DataView(ctx.memory.buffer).setUint8(ctx.storagePtr, ctx.vals[0]);
  
  ctx.storagePtr += 1;
}

function _lowerFlatU32(ctx) {
  _debugLog('[_lowerFlatU32()] args', { ctx });
  
  if (ctx.vals.length !== 1) {
    throw new Error(`expected single value to lower, got [${ctx.vals.length}]`);
  }
  
  const rem = ctx.storagePtr % 4;
  if (rem !== 0) { ctx.storagePtr += (4 - rem); }
  
  _requireValidNumericPrimitive.bind('u32', ctx.vals[0]);
  new DataView(ctx.memory.buffer).setUint32(ctx.storagePtr, ctx.vals[0], true);
  
  ctx.storagePtr += 4;
}

function _lowerFlatList(meta) {
  const {
    elemLowerFn,
    knownLen,
    size32,
    align32,
    elemSize32,
    elemAlign32,
  } = meta;
  
  if (!elemLowerFn) { throw new TypeError("missing/invalid element lower fn for list"); }
  
  return function _lowerFlatListInner(ctx) {
    _debugLog('[_lowerFlatList()] args', { ctx });
    
    if (ctx.useDirectParams) {
      if (ctx.params.length < 2) { throw new Error('insufficient params left to lower list'); }
      const storagePtr = ctx.params[0];
      const elemCount = ctx.params[1];
      ctx.params = ctx.params.slice(2);
      
      const list = ctx.vals[0];
      if (!list) { throw new Error("missing direct param value"); }
      
      const lowerCtx = {
        storagePtr,
        memory: ctx.memory,
        stringEncoding: ctx.stringEncoding,
      };
      for (let idx = 0; idx < list.length; idx++) {
        const elemPtr = storagePtr + idx * elemSize32;
        lowerCtx.storagePtr = elemPtr;
        lowerCtx.vals = list.slice(idx, idx+1);
        elemLowerFn(lowerCtx);
        lowerCtx.storagePtr = Math.max(lowerCtx.storagePtr, elemPtr + elemSize32);
      }
      ctx.storagePtr = lowerCtx.storagePtr;
      
      // TODO: implement parma-only known-length processing
      
      return;
    }
    
    // TODO(fix): is it possible to get a vals that are a addr and length here from
    // a component lower?
    
    const elems = ctx.vals[0];
    if (knownLen === undefined) {
      // unknown length
      if (!ctx.realloc) { throw new Error('missing realloc during flat string lower'); }
      const dataPtr = ctx.realloc(0, 0, elemAlign32, elemSize32 * elems.length);
      
      ctx.vals[0] = dataPtr;
      _lowerFlatU32(ctx);
      
      ctx.vals[0] = elems.length;
      _lowerFlatU32(ctx);
      
      const origPtr = ctx.storagePtr;
      ctx.storagePtr = dataPtr;
      
      for (const [idx, elem] of elems.entries()) {
        const elemPtr = dataPtr + idx * elemSize32;
        ctx.storagePtr = elemPtr;
        ctx.vals = [elem];
        elemLowerFn(ctx);
        ctx.storagePtr = Math.max(ctx.storagePtr, elemPtr + elemSize32);
      }
      
      ctx.storagePtr = origPtr;
      
    } else {
      // known length
      
      if (elems.length !== knownLen) {
        throw new TypeError(`invalid list input of length [${elems.length}], must be length [${knownLen}]`);
      }
      
      const originalPtr = ctx.storagePtr;
      for (const [idx, elem] of elems.entries()) {
        const elemPtr = originalPtr + idx * elemSize32;
        ctx.storagePtr = elemPtr;
        ctx.vals = [elem];
        elemLowerFn(ctx);
        ctx.storagePtr = Math.max(ctx.storagePtr, elemPtr + elemSize32);
      }
    }
    
    // TODO(fix): special case for u8/u16/etc, we can do a direct copy
    
    const totalSizeBytes = elems.length * size32;
    if (ctx.storageLen !== undefined && totalSizeBytes > ctx.storageLen) {
      throw new Error('not enough storage remaining for list flat lower');
    }
  }
}

function _trackHostOperation(operation) {
  const result = operation();
  if (result === null ||
  (typeof result !== 'object' && typeof result !== 'function') ||
  typeof result.then !== 'function') {
    return result;
  }
  
  STORE_ASYNC_STATE.pendingHostOperations++;
  return Promise.resolve(result).finally(() => {
    STORE_ASYNC_STATE.pendingHostOperations--;
    if (STORE_ASYNC_STATE.pendingHostOperations < 0) {
      throw new Error('negative pending host operation count');
    }
    for (const state of ASYNC_STATE.values()) { state.runTickLoop(); }
    _checkForDeadlock();
  });
}

function _guardMayLeave(componentIdx, fn) {
  return function (...args) {
    _checkMayLeave(componentIdx);
    return fn.apply(this, args);
  };
}

const base64Compile = str => WebAssembly.compile(
typeof Buffer !== 'undefined'
? Buffer.from(str, 'base64')
: Uint8Array.from(atob(str), b => b.charCodeAt(0))
);


const isNode = typeof process !== 'undefined' && process.versions && process.versions.node;
let _fs;
async function fetchCompile (url) {
  if (isNode) {
    _fs = _fs || await import('node:fs/promises');
    return WebAssembly.compile(await _fs.readFile(url));
  }
  return fetch(url).then(WebAssembly.compileStreaming);
}

const symbolRscHandle = Symbol('handle');
const symbolDispose = Symbol.dispose || Symbol.for('dispose');

const HANDLE_TABLES= [];


function finalizationRegistryCreate (unregister) {
  if (typeof FinalizationRegistry === 'undefined') {
    return { unregister () {} };
  }
  return new FinalizationRegistry(unregister);
}

class ComponentError extends Error {
  constructor (value) {
    const enumerable = typeof value !== 'string';
    super(enumerable ? `${String(value)} (see error.payload)` : value);
    Object.defineProperty(this, 'payload', { value, enumerable });
  }
}

function throwInvalidBool() {
  throw new TypeError('invalid variant discriminant for bool');
}

const instantiateCore = WebAssembly.instantiate;

function _suspendingImport(componentIdx, fn, syncOnly = false, switchesTask = false) {
  return function (...args) {
    _checkMayLeave(componentIdx);
    const saved = CURRENT_TASK_META[componentIdx] ?? null;
    
    const savedTask = saved
    ? getCurrentTask(saved.componentIdx, saved.taskID)?.task
    : null;
    const mayBlock = savedTask
    ? (savedTask?.mayBlock() ?? (CURRENT_TASK_MAY_BLOCK.value !== 0))
    : false;
    if (!saved && !mayBlock) {
      throw new WebAssemblyRuntimeError('cannot block a synchronous task before returning');
    }
    
    if (syncOnly || !mayBlock) {
      let result;
      try {
        result = fn.apply(null, args);
      } catch (err) {
        CURRENT_TASK_META[componentIdx] = saved;
        if (!switchesTask) { CURRENT_TASK_META.current = saved; }
        throw err;
      }
      
      CURRENT_TASK_META[componentIdx] = saved;
      if (!switchesTask) { CURRENT_TASK_META.current = saved; }
      
      if (result !== null &&
      (typeof result === 'object' || typeof result === 'function') &&
      typeof result.then === 'function') {
        // The helper may already have returned a rejected promise.
        // Mark it handled before replacing it with the canonical
        // synchronous-task trap.
        Promise.resolve(result).catch(() => {});
        throw new WebAssemblyRuntimeError('cannot block a synchronous task before returning');
      }
      return result;
    }
    
    return (async () => {
      try {
        return await fn.apply(null, args);
      } finally {
        CURRENT_TASK_META[componentIdx] = saved;
        if (!switchesTask) { CURRENT_TASK_META.current = saved; }
      }
    })();
  };
}


let exports0;
let exports1;
let memory0;
let realloc0;
let realloc0Async;

const _trampoline7 = function(arg0, arg1) {
  _debugLog('[iface="wasi:random/random@0.2.6", function="get-random-bytes"] [Instruction::CallInterface] (sync, @ enter)');
  const hostProvided = true;
  
  let parentTask;
  let task;
  let subtask;
  
  const createTask = () => {
    const results = createNewCurrentTask({
      componentIdx: -1,
      isAsync: false,
      entryFnName: 'getRandomBytes',
      getCallbackFn: () => null,
      callbackFnName: null,
      errHandling: 'none',
      callingWasmExport: false,
    });
    task = results[0];
  };
  
  taskCreation: {
    parentTask = getCurrentTask(
    0,
    _getGlobalCurrentTaskMeta(0)?.taskID,
    )?.task;
    
    if (!parentTask) {
      createTask();
      break taskCreation;
    }
    
    createTask();
    
    if (hostProvided) {
      subtask = parentTask.getLatestSubtask();
      if (!subtask) {
        throw new Error(`Missing subtask (in parent task [${parentTask.id()}]) for host import, has the import been lowered? (ensure asyncImports are set properly)`);
      }
      task.setParentSubtask(subtask);
    }
  }
  
  const started = task.enterSync();
  let ret;
  
  
  try {
    ret = _withGlobalCurrentTaskMeta({
      componentIdx: task.componentIdx(),
      taskID: task.id(),
      fn: () => _trackHostOperation(() => getRandomBytes(BigInt.asUintN(64, BigInt(arg0)))),
    })
    ;
  } catch (err) {
    
    _debugLog('[Instruction::CallInterface] error during sync call', {
      taskID: task.id(),
      subtaskID: task.getParentSubtask()?.id(),
      err,
    });
    getOrCreateAsyncState(0).markTrapped(err);
    task.setErrored(err);
    task.reject(err);
    task.exit();
    throw err;
    
  }
  
  var val0 = ret;
  var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
  var ptr0 = realloc0(0, 0, 1, len0 * 1);
  
  let valData0;
  const valLenBytes0 = len0 * 1;
  if (Array.isArray(val0)) {
    // Regular array likely containing numbers, write values to memory
    let offset = 0;
    const dv0 = new DataView(memory0.buffer);
    for (const v of val0) {
      _requireValidNumericPrimitive.bind(null, 'u8')(v);
      dv0.setUint8(ptr0+ offset, v, true);
      offset += 1;
    }
  } else {
    // TypedArray / ArrayBuffer-like, direct copy
    valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
    const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
    out0.set(valData0);
  }
  
  dataView(memory0).setUint32(arg1 + 4, len0, true);
  dataView(memory0).setUint32(arg1 + 0, ptr0, true);
  _debugLog('[iface="wasi:random/random@0.2.6", function="get-random-bytes"][Instruction::Return]', {
    funcName: 'get-random-bytes',
    paramCount: 0,
    async: false,
    postReturn: false
  });
  task.resolve([ret]);
  task.exit();
}
_trampoline7.fnName = 'wasi:random/random@0.2.6#getRandomBytes';
let exports2;
let postReturn0;
let postReturn0Async;
let postReturn1;
let postReturn1Async;
let postReturn2;
let postReturn2Async;
let postReturn3;
let postReturn3Async;
let postReturn4;
let postReturn4Async;
let postReturn5;
let postReturn5Async;
let postReturn6;
let postReturn6Async;
let postReturn7;
let postReturn7Async;
let postReturn8;
let postReturn8Async;
let postReturn9;
let postReturn9Async;
let postReturn10;
let postReturn10Async;
let postReturn11;
let postReturn11Async;
let postReturn12;
let postReturn12Async;
let postReturn13;
let postReturn13Async;
let postReturn14;
let postReturn14Async;
let postReturn15;
let postReturn15Async;
let postReturn16;
let postReturn16Async;
let postReturn17;
let postReturn17Async;
let postReturn18;
let postReturn18Async;
let postReturn19;
let postReturn19Async;
let postReturn20;
let postReturn20Async;
let postReturn21;
let postReturn21Async;
let postReturn22;
let postReturn22Async;
let codec010Base45Encode;

function base45Encode(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010Base45Encode',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="base45-encode"][Instruction::CallWasm] enter', {
          funcName: 'base45-encode',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010Base45Encode(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr1 = dataView(memory0).getUint32(ret + 0, true);
        var len1 = dataView(memory0).getUint32(ret + 4, true);
        var result1 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr1, len1));
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="base45-encode"][Instruction::Return]', {
          funcName: 'base45-encode',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result1]);
        const retCopy = result1;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let codec010Base45Decode;

function base45Decode(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010Base45Decode',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="base45-decode"][Instruction::CallWasm] enter', {
          funcName: 'base45-decode',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010Base45Decode(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant8;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr1 = dataView(memory0).getUint32(ret + 4, true);
            var len1 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr1 % 1 !== 0) throw new TypeError(`list pointer [${ptr1}] is not aligned to 1`);
            var result1 = new Uint8Array(memory0.buffer.slice(ptr1, ptr1 + len1 * 1));
            variant8= {
              tag: 'ok',
              val: result1
            };
            break;
          }
          case 1: {
            let variant7;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr2 = dataView(memory0).getUint32(ret + 8, true);
                var len2 = dataView(memory0).getUint32(ret + 12, true);
                var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
                variant7= {
                  tag: 'base45-invalid',
                  val: result2
                };
                break;
              }
              case 1: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant7= {
                  tag: 'inflate-failed',
                  val: result3
                };
                break;
              }
              case 2: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant7= {
                  tag: 'limit-exceeded',
                  val: result4
                };
                break;
              }
              case 3: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant7= {
                  tag: 'cbor-malformed',
                  val: result5
                };
                break;
              }
              case 4: {
                variant7= {
                  tag: 'bad-prefix',
                };
                break;
              }
              case 5: {
                var ptr6 = dataView(memory0).getUint32(ret + 8, true);
                var len6 = dataView(memory0).getUint32(ret + 12, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant7= {
                  tag: 'unsupported-context-version',
                  val: result6
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CodecError');
              }
            }
            variant8= {
              tag: 'err',
              val: variant7
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="base45-decode"][Instruction::Return]', {
          funcName: 'base45-decode',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant8;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn1(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let codec010ZlibCompress;

function zlibCompress(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010ZlibCompress',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="zlib-compress"][Instruction::CallWasm] enter', {
          funcName: 'zlib-compress',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010ZlibCompress(ptr0, len0, toUint8(arg1)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr1 = dataView(memory0).getUint32(ret + 0, true);
        var len1 = dataView(memory0).getUint32(ret + 4, true);
        if (ptr1 % 1 !== 0) throw new TypeError(`list pointer [${ptr1}] is not aligned to 1`);
        var result1 = new Uint8Array(memory0.buffer.slice(ptr1, ptr1 + len1 * 1));
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="zlib-compress"][Instruction::Return]', {
          funcName: 'zlib-compress',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result1]);
        const retCopy = result1;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let codec010ZlibInflate;

function zlibInflate(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010ZlibInflate',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="zlib-inflate"][Instruction::CallWasm] enter', {
          funcName: 'zlib-inflate',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010ZlibInflate(ptr0, len0, toUint32(arg1)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant8;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr1 = dataView(memory0).getUint32(ret + 4, true);
            var len1 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr1 % 1 !== 0) throw new TypeError(`list pointer [${ptr1}] is not aligned to 1`);
            var result1 = new Uint8Array(memory0.buffer.slice(ptr1, ptr1 + len1 * 1));
            variant8= {
              tag: 'ok',
              val: result1
            };
            break;
          }
          case 1: {
            let variant7;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr2 = dataView(memory0).getUint32(ret + 8, true);
                var len2 = dataView(memory0).getUint32(ret + 12, true);
                var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
                variant7= {
                  tag: 'base45-invalid',
                  val: result2
                };
                break;
              }
              case 1: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant7= {
                  tag: 'inflate-failed',
                  val: result3
                };
                break;
              }
              case 2: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant7= {
                  tag: 'limit-exceeded',
                  val: result4
                };
                break;
              }
              case 3: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant7= {
                  tag: 'cbor-malformed',
                  val: result5
                };
                break;
              }
              case 4: {
                variant7= {
                  tag: 'bad-prefix',
                };
                break;
              }
              case 5: {
                var ptr6 = dataView(memory0).getUint32(ret + 8, true);
                var len6 = dataView(memory0).getUint32(ret + 12, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant7= {
                  tag: 'unsupported-context-version',
                  val: result6
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CodecError');
              }
            }
            variant8= {
              tag: 'err',
              val: variant7
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="zlib-inflate"][Instruction::Return]', {
          funcName: 'zlib-inflate',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant8;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn1(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let codec010DetectContext;

function detectContext(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010DetectContext',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="detect-context"][Instruction::CallWasm] enter', {
          funcName: 'detect-context',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010DetectContext(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant9;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr1 = dataView(memory0).getUint32(ret + 4, true);
            var len1 = dataView(memory0).getUint32(ret + 8, true);
            var result1 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr1, len1));
            var ptr2 = dataView(memory0).getUint32(ret + 12, true);
            var len2 = dataView(memory0).getUint32(ret + 16, true);
            var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
            variant9= {
              tag: 'ok',
              val: [result1, result2]
            };
            break;
          }
          case 1: {
            let variant8;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant8= {
                  tag: 'base45-invalid',
                  val: result3
                };
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant8= {
                  tag: 'inflate-failed',
                  val: result4
                };
                break;
              }
              case 2: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant8= {
                  tag: 'limit-exceeded',
                  val: result5
                };
                break;
              }
              case 3: {
                var ptr6 = dataView(memory0).getUint32(ret + 8, true);
                var len6 = dataView(memory0).getUint32(ret + 12, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant8= {
                  tag: 'cbor-malformed',
                  val: result6
                };
                break;
              }
              case 4: {
                variant8= {
                  tag: 'bad-prefix',
                };
                break;
              }
              case 5: {
                var ptr7 = dataView(memory0).getUint32(ret + 8, true);
                var len7 = dataView(memory0).getUint32(ret + 12, true);
                var result7 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr7, len7));
                variant8= {
                  tag: 'unsupported-context-version',
                  val: result7
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CodecError');
              }
            }
            variant9= {
              tag: 'err',
              val: variant8
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="detect-context"][Instruction::Return]', {
          funcName: 'detect-context',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant9;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn2(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let codec010ToHc1;

function toHc1(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010ToHc1',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="to-hc1"][Instruction::CallWasm] enter', {
          funcName: 'to-hc1',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010ToHc1(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr1 = dataView(memory0).getUint32(ret + 0, true);
        var len1 = dataView(memory0).getUint32(ret + 4, true);
        var result1 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr1, len1));
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="to-hc1"][Instruction::Return]', {
          funcName: 'to-hc1',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result1]);
        const retCopy = result1;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let codec010FromHc1;

function fromHc1(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010FromHc1',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        var {maxInputChars: v1_0, maxInflatedBytes: v1_1, maxDepth: v1_2, maxItems: v1_3, maxStringBytes: v1_4 } = arg1;
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="from-hc1"][Instruction::CallWasm] enter', {
          funcName: 'from-hc1',
          paramCount: 7,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010FromHc1(ptr0, len0, toUint32(v1_0), toUint32(v1_1), toUint32(v1_2), toUint32(v1_3), toUint32(v1_4)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant9;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr2 = dataView(memory0).getUint32(ret + 4, true);
            var len2 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr2 % 1 !== 0) throw new TypeError(`list pointer [${ptr2}] is not aligned to 1`);
            var result2 = new Uint8Array(memory0.buffer.slice(ptr2, ptr2 + len2 * 1));
            variant9= {
              tag: 'ok',
              val: result2
            };
            break;
          }
          case 1: {
            let variant8;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant8= {
                  tag: 'base45-invalid',
                  val: result3
                };
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant8= {
                  tag: 'inflate-failed',
                  val: result4
                };
                break;
              }
              case 2: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant8= {
                  tag: 'limit-exceeded',
                  val: result5
                };
                break;
              }
              case 3: {
                var ptr6 = dataView(memory0).getUint32(ret + 8, true);
                var len6 = dataView(memory0).getUint32(ret + 12, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant8= {
                  tag: 'cbor-malformed',
                  val: result6
                };
                break;
              }
              case 4: {
                variant8= {
                  tag: 'bad-prefix',
                };
                break;
              }
              case 5: {
                var ptr7 = dataView(memory0).getUint32(ret + 8, true);
                var len7 = dataView(memory0).getUint32(ret + 12, true);
                var result7 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr7, len7));
                variant8= {
                  tag: 'unsupported-context-version',
                  val: result7
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CodecError');
              }
            }
            variant9= {
              tag: 'err',
              val: variant8
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="from-hc1"][Instruction::Return]', {
          funcName: 'from-hc1',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant9;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn1(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let codec010CborDeterministic;

function cborDeterministic(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'codec010CborDeterministic',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        var {maxInputChars: v1_0, maxInflatedBytes: v1_1, maxDepth: v1_2, maxItems: v1_3, maxStringBytes: v1_4 } = arg1;
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="cbor-deterministic"][Instruction::CallWasm] enter', {
          funcName: 'cbor-deterministic',
          paramCount: 7,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => codec010CborDeterministic(ptr0, len0, toUint32(v1_0), toUint32(v1_1), toUint32(v1_2), toUint32(v1_3), toUint32(v1_4)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant9;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr2 = dataView(memory0).getUint32(ret + 4, true);
            var len2 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr2 % 1 !== 0) throw new TypeError(`list pointer [${ptr2}] is not aligned to 1`);
            var result2 = new Uint8Array(memory0.buffer.slice(ptr2, ptr2 + len2 * 1));
            variant9= {
              tag: 'ok',
              val: result2
            };
            break;
          }
          case 1: {
            let variant8;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant8= {
                  tag: 'base45-invalid',
                  val: result3
                };
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant8= {
                  tag: 'inflate-failed',
                  val: result4
                };
                break;
              }
              case 2: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant8= {
                  tag: 'limit-exceeded',
                  val: result5
                };
                break;
              }
              case 3: {
                var ptr6 = dataView(memory0).getUint32(ret + 8, true);
                var len6 = dataView(memory0).getUint32(ret + 12, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant8= {
                  tag: 'cbor-malformed',
                  val: result6
                };
                break;
              }
              case 4: {
                variant8= {
                  tag: 'bad-prefix',
                };
                break;
              }
              case 5: {
                var ptr7 = dataView(memory0).getUint32(ret + 8, true);
                var len7 = dataView(memory0).getUint32(ret + 12, true);
                var result7 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr7, len7));
                variant8= {
                  tag: 'unsupported-context-version',
                  val: result7
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CodecError');
              }
            }
            variant9= {
              tag: 'err',
              val: variant8
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/codec@0.1.0", function="cbor-deterministic"][Instruction::Return]', {
          funcName: 'cbor-deterministic',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant9;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn1(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010KidFromDsc;

function kidFromDsc(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010KidFromDsc',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="kid-from-dsc"][Instruction::CallWasm] enter', {
          funcName: 'kid-from-dsc',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010KidFromDsc(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr1 = dataView(memory0).getUint32(ret + 0, true);
        var len1 = dataView(memory0).getUint32(ret + 4, true);
        if (ptr1 % 1 !== 0) throw new TypeError(`list pointer [${ptr1}] is not aligned to 1`);
        var result1 = new Uint8Array(memory0.buffer.slice(ptr1, ptr1 + len1 * 1));
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="kid-from-dsc"][Instruction::Return]', {
          funcName: 'kid-from-dsc',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result1]);
        const retCopy = result1;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010KidToBase64;

function kidToBase64(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010KidToBase64',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        var val1 = arg1;
        let enum1;
        switch (val1) {
          case 'standard': {
            enum1 = 0;
            break;
          }
          case 'url-safe': {
            enum1 = 1;
            break;
          }
          default: {
            if ((arg1) instanceof Error) {
              console.error(arg1);
            }
            
            throw new TypeError(`"${val1}" is not one of the cases of base64-alphabet`);
          }
        }
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="kid-to-base64"][Instruction::CallWasm] enter', {
          funcName: 'kid-to-base64',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010KidToBase64(ptr0, len0, enum1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr2 = dataView(memory0).getUint32(ret + 0, true);
        var len2 = dataView(memory0).getUint32(ret + 4, true);
        var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="kid-to-base64"][Instruction::Return]', {
          funcName: 'kid-to-base64',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result2]);
        const retCopy = result2;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010KidFromBase64;

function kidFromBase64(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010KidFromBase64',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="kid-from-base64"][Instruction::CallWasm] enter', {
          funcName: 'kid-from-base64',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010KidFromBase64(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant7;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr1 = dataView(memory0).getUint32(ret + 4, true);
            var len1 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr1 % 1 !== 0) throw new TypeError(`list pointer [${ptr1}] is not aligned to 1`);
            var result1 = new Uint8Array(memory0.buffer.slice(ptr1, ptr1 + len1 * 1));
            variant7= {
              tag: 'ok',
              val: result1
            };
            break;
          }
          case 1: {
            let variant6;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr2 = dataView(memory0).getUint32(ret + 8, true);
                var len2 = dataView(memory0).getUint32(ret + 12, true);
                var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
                variant6= {
                  tag: 'malformed',
                  val: result2
                };
                break;
              }
              case 1: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant6= {
                  tag: 'header-invalid',
                  val: result3
                };
                break;
              }
              case 2: {
                variant6= {
                  tag: 'unsupported-algorithm',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 3: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant6= {
                  tag: 'bad-signature-format',
                  val: result4
                };
                break;
              }
              case 4: {
                variant6= {
                  tag: 'signature-invalid',
                };
                break;
              }
              case 5: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant6= {
                  tag: 'bad-certificate',
                  val: result5
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CoseError');
              }
            }
            variant7= {
              tag: 'err',
              val: variant6
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="kid-from-base64"][Instruction::Return]', {
          funcName: 'kid-from-base64',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant7;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn3(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010ParseSign1;

function parseSign1(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010ParseSign1',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        var {maxInputChars: v1_0, maxInflatedBytes: v1_1, maxDepth: v1_2, maxItems: v1_3, maxStringBytes: v1_4 } = arg1;
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="parse-sign1"][Instruction::CallWasm] enter', {
          funcName: 'parse-sign1',
          paramCount: 7,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010ParseSign1(ptr0, len0, toUint32(v1_0), toUint32(v1_1), toUint32(v1_2), toUint32(v1_3), toUint32(v1_4)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant16;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr2 = dataView(memory0).getUint32(ret + 4, true);
            var len2 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr2 % 1 !== 0) throw new TypeError(`list pointer [${ptr2}] is not aligned to 1`);
            var result2 = new Uint8Array(memory0.buffer.slice(ptr2, ptr2 + len2 * 1));
            var ptr3 = dataView(memory0).getUint32(ret + 12, true);
            var len3 = dataView(memory0).getUint32(ret + 16, true);
            if (ptr3 % 1 !== 0) throw new TypeError(`list pointer [${ptr3}] is not aligned to 1`);
            var result3 = new Uint8Array(memory0.buffer.slice(ptr3, ptr3 + len3 * 1));
            var ptr4 = dataView(memory0).getUint32(ret + 20, true);
            var len4 = dataView(memory0).getUint32(ret + 24, true);
            if (ptr4 % 1 !== 0) throw new TypeError(`list pointer [${ptr4}] is not aligned to 1`);
            var result4 = new Uint8Array(memory0.buffer.slice(ptr4, ptr4 + len4 * 1));
            let variant6;
            switch (dataView(memory0).getUint8(ret + 28, true)) {
              case 0: {
                variant6 = undefined;
                break;
              }
              case 1: {
                let enum5;
                switch (dataView(memory0).getUint8(ret + 29, true)) {
                  case 0: {
                    enum5 = 'es256';
                    break;
                  }
                  case 1: {
                    enum5 = 'ps256';
                    break;
                  }
                  default: {
                    throw new TypeError('invalid discriminant specified for CoseAlgorithm');
                  }
                }
                variant6 = enum5;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant8;
            switch (dataView(memory0).getUint8(ret + 32, true)) {
              case 0: {
                variant8 = undefined;
                break;
              }
              case 1: {
                var ptr7 = dataView(memory0).getUint32(ret + 36, true);
                var len7 = dataView(memory0).getUint32(ret + 40, true);
                if (ptr7 % 1 !== 0) throw new TypeError(`list pointer [${ptr7}] is not aligned to 1`);
                var result7 = new Uint8Array(memory0.buffer.slice(ptr7, ptr7 + len7 * 1));
                variant8 = result7;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var bool9 = dataView(memory0).getUint8(ret + 44, true);
            var bool10 = dataView(memory0).getUint8(ret + 45, true);
            variant16= {
              tag: 'ok',
              val: {
                protected: result2,
                payload: result3,
                signature: result4,
                alg: variant6,
                kid: variant8,
                kidInProtected: bool9 == 0 ? false : (bool9 == 1 ? true : throwInvalidBool()),
                tagged: bool10 == 0 ? false : (bool10 == 1 ? true : throwInvalidBool()),
              }
            };
            break;
          }
          case 1: {
            let variant15;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr11 = dataView(memory0).getUint32(ret + 8, true);
                var len11 = dataView(memory0).getUint32(ret + 12, true);
                var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                variant15= {
                  tag: 'malformed',
                  val: result11
                };
                break;
              }
              case 1: {
                var ptr12 = dataView(memory0).getUint32(ret + 8, true);
                var len12 = dataView(memory0).getUint32(ret + 12, true);
                var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                variant15= {
                  tag: 'header-invalid',
                  val: result12
                };
                break;
              }
              case 2: {
                variant15= {
                  tag: 'unsupported-algorithm',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 3: {
                var ptr13 = dataView(memory0).getUint32(ret + 8, true);
                var len13 = dataView(memory0).getUint32(ret + 12, true);
                var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
                variant15= {
                  tag: 'bad-signature-format',
                  val: result13
                };
                break;
              }
              case 4: {
                variant15= {
                  tag: 'signature-invalid',
                };
                break;
              }
              case 5: {
                var ptr14 = dataView(memory0).getUint32(ret + 8, true);
                var len14 = dataView(memory0).getUint32(ret + 12, true);
                var result14 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr14, len14));
                variant15= {
                  tag: 'bad-certificate',
                  val: result14
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CoseError');
              }
            }
            variant16= {
              tag: 'err',
              val: variant15
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="parse-sign1"][Instruction::Return]', {
          funcName: 'parse-sign1',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant16;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn4(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010SigStructure;

function sigStructure(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010SigStructure',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        var val1 = arg1;
        var len1 = Array.isArray(val1) ? val1.length : val1.byteLength;
        var ptr1 = realloc0(0, 0, 1, len1 * 1);
        
        let valData1;
        const valLenBytes1 = len1 * 1;
        if (Array.isArray(val1)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv1 = new DataView(memory0.buffer);
          for (const v of val1) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv1.setUint8(ptr1+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData1 = new Uint8Array(val1.buffer || val1, val1.byteOffset, valLenBytes1);
          const out1 = new Uint8Array(memory0.buffer, ptr1, valLenBytes1);
          out1.set(valData1);
        }
        
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="sig-structure"][Instruction::CallWasm] enter', {
          funcName: 'sig-structure',
          paramCount: 4,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010SigStructure(ptr0, len0, ptr1, len1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr2 = dataView(memory0).getUint32(ret + 0, true);
        var len2 = dataView(memory0).getUint32(ret + 4, true);
        if (ptr2 % 1 !== 0) throw new TypeError(`list pointer [${ptr2}] is not aligned to 1`);
        var result2 = new Uint8Array(memory0.buffer.slice(ptr2, ptr2 + len2 * 1));
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="sig-structure"][Instruction::Return]', {
          funcName: 'sig-structure',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result2]);
        const retCopy = result2;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010VerifySign1;

function verifySign1(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010VerifySign1',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var {protected: v0_0, payload: v0_1, signature: v0_2, alg: v0_3, kid: v0_4, kidInProtected: v0_5, tagged: v0_6 } = arg0;
        var val1 = v0_0;
        var len1 = Array.isArray(val1) ? val1.length : val1.byteLength;
        var ptr1 = realloc0(0, 0, 1, len1 * 1);
        
        let valData1;
        const valLenBytes1 = len1 * 1;
        if (Array.isArray(val1)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv1 = new DataView(memory0.buffer);
          for (const v of val1) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv1.setUint8(ptr1+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData1 = new Uint8Array(val1.buffer || val1, val1.byteOffset, valLenBytes1);
          const out1 = new Uint8Array(memory0.buffer, ptr1, valLenBytes1);
          out1.set(valData1);
        }
        
        var val2 = v0_1;
        var len2 = Array.isArray(val2) ? val2.length : val2.byteLength;
        var ptr2 = realloc0(0, 0, 1, len2 * 1);
        
        let valData2;
        const valLenBytes2 = len2 * 1;
        if (Array.isArray(val2)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv2 = new DataView(memory0.buffer);
          for (const v of val2) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv2.setUint8(ptr2+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData2 = new Uint8Array(val2.buffer || val2, val2.byteOffset, valLenBytes2);
          const out2 = new Uint8Array(memory0.buffer, ptr2, valLenBytes2);
          out2.set(valData2);
        }
        
        var val3 = v0_2;
        var len3 = Array.isArray(val3) ? val3.length : val3.byteLength;
        var ptr3 = realloc0(0, 0, 1, len3 * 1);
        
        let valData3;
        const valLenBytes3 = len3 * 1;
        if (Array.isArray(val3)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv3 = new DataView(memory0.buffer);
          for (const v of val3) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv3.setUint8(ptr3+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData3 = new Uint8Array(val3.buffer || val3, val3.byteOffset, valLenBytes3);
          const out3 = new Uint8Array(memory0.buffer, ptr3, valLenBytes3);
          out3.set(valData3);
        }
        
        var variant5 = v0_3;
        let variant5_0;
        let variant5_1;
        if (variant5 === null || variant5=== undefined) {
          variant5_0 = 0;
          variant5_1 = 0;
        } else {
          const e = variant5;
          var val4 = e;
          let enum4;
          switch (val4) {
            case 'es256': {
              enum4 = 0;
              break;
            }
            case 'ps256': {
              enum4 = 1;
              break;
            }
            default: {
              if ((e) instanceof Error) {
                console.error(e);
              }
              
              throw new TypeError(`"${val4}" is not one of the cases of cose-algorithm`);
            }
          }
          variant5_0 = 1;
          variant5_1 = enum4;
        }
        var variant7 = v0_4;
        let variant7_0;
        let variant7_1;
        let variant7_2;
        if (variant7 === null || variant7=== undefined) {
          variant7_0 = 0;
          variant7_1 = 0;
          variant7_2 = 0;
        } else {
          const e = variant7;
          var val6 = e;
          var len6 = Array.isArray(val6) ? val6.length : val6.byteLength;
          var ptr6 = realloc0(0, 0, 1, len6 * 1);
          
          let valData6;
          const valLenBytes6 = len6 * 1;
          if (Array.isArray(val6)) {
            // Regular array likely containing numbers, write values to memory
            let offset = 0;
            const dv6 = new DataView(memory0.buffer);
            for (const v of val6) {
              _requireValidNumericPrimitive.bind(null, 'u8')(v);
              dv6.setUint8(ptr6+ offset, v, true);
              offset += 1;
            }
          } else {
            // TypedArray / ArrayBuffer-like, direct copy
            valData6 = new Uint8Array(val6.buffer || val6, val6.byteOffset, valLenBytes6);
            const out6 = new Uint8Array(memory0.buffer, ptr6, valLenBytes6);
            out6.set(valData6);
          }
          
          variant7_0 = 1;
          variant7_1 = ptr6;
          variant7_2 = len6;
        }
        var val8 = arg1;
        var len8 = Array.isArray(val8) ? val8.length : val8.byteLength;
        var ptr8 = realloc0(0, 0, 1, len8 * 1);
        
        let valData8;
        const valLenBytes8 = len8 * 1;
        if (Array.isArray(val8)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv8 = new DataView(memory0.buffer);
          for (const v of val8) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv8.setUint8(ptr8+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData8 = new Uint8Array(val8.buffer || val8, val8.byteOffset, valLenBytes8);
          const out8 = new Uint8Array(memory0.buffer, ptr8, valLenBytes8);
          out8.set(valData8);
        }
        
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="verify-sign1"][Instruction::CallWasm] enter', {
          funcName: 'verify-sign1',
          paramCount: 15,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010VerifySign1(ptr1, len1, ptr2, len2, ptr3, len3, variant5_0, variant5_1, variant7_0, variant7_1, variant7_2, v0_5 ? 1 : 0, v0_6 ? 1 : 0, ptr8, len8),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant14;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant14= {
              tag: 'ok',
              val: undefined
            };
            break;
          }
          case 1: {
            let variant13;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr9 = dataView(memory0).getUint32(ret + 8, true);
                var len9 = dataView(memory0).getUint32(ret + 12, true);
                var result9 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr9, len9));
                variant13= {
                  tag: 'malformed',
                  val: result9
                };
                break;
              }
              case 1: {
                var ptr10 = dataView(memory0).getUint32(ret + 8, true);
                var len10 = dataView(memory0).getUint32(ret + 12, true);
                var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                variant13= {
                  tag: 'header-invalid',
                  val: result10
                };
                break;
              }
              case 2: {
                variant13= {
                  tag: 'unsupported-algorithm',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 3: {
                var ptr11 = dataView(memory0).getUint32(ret + 8, true);
                var len11 = dataView(memory0).getUint32(ret + 12, true);
                var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                variant13= {
                  tag: 'bad-signature-format',
                  val: result11
                };
                break;
              }
              case 4: {
                variant13= {
                  tag: 'signature-invalid',
                };
                break;
              }
              case 5: {
                var ptr12 = dataView(memory0).getUint32(ret + 8, true);
                var len12 = dataView(memory0).getUint32(ret + 12, true);
                var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                variant13= {
                  tag: 'bad-certificate',
                  val: result12
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CoseError');
              }
            }
            variant14= {
              tag: 'err',
              val: variant13
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="verify-sign1"][Instruction::Return]', {
          funcName: 'verify-sign1',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant14;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn5(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010EcdsaDerToRaw;

function ecdsaDerToRaw(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010EcdsaDerToRaw',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="ecdsa-der-to-raw"][Instruction::CallWasm] enter', {
          funcName: 'ecdsa-der-to-raw',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010EcdsaDerToRaw(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant7;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr1 = dataView(memory0).getUint32(ret + 4, true);
            var len1 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr1 % 1 !== 0) throw new TypeError(`list pointer [${ptr1}] is not aligned to 1`);
            var result1 = new Uint8Array(memory0.buffer.slice(ptr1, ptr1 + len1 * 1));
            variant7= {
              tag: 'ok',
              val: result1
            };
            break;
          }
          case 1: {
            let variant6;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr2 = dataView(memory0).getUint32(ret + 8, true);
                var len2 = dataView(memory0).getUint32(ret + 12, true);
                var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
                variant6= {
                  tag: 'malformed',
                  val: result2
                };
                break;
              }
              case 1: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant6= {
                  tag: 'header-invalid',
                  val: result3
                };
                break;
              }
              case 2: {
                variant6= {
                  tag: 'unsupported-algorithm',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 3: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant6= {
                  tag: 'bad-signature-format',
                  val: result4
                };
                break;
              }
              case 4: {
                variant6= {
                  tag: 'signature-invalid',
                };
                break;
              }
              case 5: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant6= {
                  tag: 'bad-certificate',
                  val: result5
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CoseError');
              }
            }
            variant7= {
              tag: 'err',
              val: variant6
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="ecdsa-der-to-raw"][Instruction::Return]', {
          funcName: 'ecdsa-der-to-raw',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant7;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn3(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let cose010EcdsaRawToDer;

function ecdsaRawToDer(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'cose010EcdsaRawToDer',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="ecdsa-raw-to-der"][Instruction::CallWasm] enter', {
          funcName: 'ecdsa-raw-to-der',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => cose010EcdsaRawToDer(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant7;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr1 = dataView(memory0).getUint32(ret + 4, true);
            var len1 = dataView(memory0).getUint32(ret + 8, true);
            if (ptr1 % 1 !== 0) throw new TypeError(`list pointer [${ptr1}] is not aligned to 1`);
            var result1 = new Uint8Array(memory0.buffer.slice(ptr1, ptr1 + len1 * 1));
            variant7= {
              tag: 'ok',
              val: result1
            };
            break;
          }
          case 1: {
            let variant6;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr2 = dataView(memory0).getUint32(ret + 8, true);
                var len2 = dataView(memory0).getUint32(ret + 12, true);
                var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
                variant6= {
                  tag: 'malformed',
                  val: result2
                };
                break;
              }
              case 1: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant6= {
                  tag: 'header-invalid',
                  val: result3
                };
                break;
              }
              case 2: {
                variant6= {
                  tag: 'unsupported-algorithm',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 3: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant6= {
                  tag: 'bad-signature-format',
                  val: result4
                };
                break;
              }
              case 4: {
                variant6= {
                  tag: 'signature-invalid',
                };
                break;
              }
              case 5: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant6= {
                  tag: 'bad-certificate',
                  val: result5
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for CoseError');
              }
            }
            variant7= {
              tag: 'err',
              val: variant6
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/cose@0.1.0", function="ecdsa-raw-to-der"][Instruction::Return]', {
          funcName: 'ecdsa-raw-to-der',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant7;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn3(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}

const handleTable2 = [T_FLAG, 0];
handleTable2._createdReps = new Set();
handleTable2._componentIdx = 0;
const finalizationRegistry2 = finalizationRegistryCreate((handle) => {
  const { rep } = rscTableRemove(handleTable2, handle);
  exports0['2'](rep);
});

HANDLE_TABLES[2] = handleTable2;
let subclaims010ConstructorRegistry;

class Registry{
  constructor() {
    
    const hostProvided = false;
    getOrCreateAsyncState(0).throwIfTrapped();
    
    const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
      componentIdx: 0,
      isAsync: false,
      isManualAsync: false,
      preserveFutureResult: false,
      entryFnName: 'subclaims010ConstructorRegistry',
      getCallbackFn: () => null,
      callbackFnName: null,
      errHandling: 'none',
      callingWasmExport: true,
    });
    task.setCalleeIsAsync(false);
    
    const started = task.enterSync();
    CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
    
    if (null!== null) {
      task.setReturnMemoryIdx(null);
      task.setReturnMemory((() => null)());
    }
    
    
    return _withGlobalCurrentTaskMeta({
      taskID: task.id(),
      componentIdx: task.componentIdx(),
      fn: () => {
        try {
          
          _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[constructor]registry"][Instruction::CallWasm] enter', {
            funcName: '[constructor]registry',
            paramCount: 0,
            async: false,
            postReturn: false,
          });
          
          let ret;
          
          try {
            ret =  _withGlobalCurrentTaskMeta({
              taskID: task.id(),
              componentIdx: task.componentIdx(),
              fn: () => subclaims010ConstructorRegistry(),
            });
          } catch (err) {
            
            _debugLog('[Instruction::CallWasm] error during sync call', {
              taskID: task.id(),
              err,
            });
            getOrCreateAsyncState(0).markTrapped(err);
            task.setErrored(err);
            task.reject(err);
            task.exit();
            throw err;
            
          }
          
          var handle1 = ret;
          var rsc0 = new.target === Registry ? this : Object.create(Registry.prototype);
          Object.defineProperty(rsc0, symbolRscHandle, { writable: true, value: handle1});
          finalizationRegistry2.register(rsc0, handle1, rsc0);
          Object.defineProperty(rsc0, symbolDispose, { writable: true, value: function () {
            finalizationRegistry2.unregister(rsc0);
            const handleEntry = rscTableRemove(handleTable2, handle1);
            rsc0[symbolDispose] = emptyFunc;
            rsc0[symbolRscHandle] = undefined;
            exports0['2'](handleEntry.rep);
          }});
          _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[constructor]registry"][Instruction::Return]', {
            funcName: '[constructor]registry',
            paramCount: 1,
            async: false,
            postReturn: false
          });
          task.resolve([rsc0]);
          task.exit();
          return rsc0;
          
        } catch (err) {
          if (!task.isResolvedState()) {
            task.setErrored(err);
            task.reject(err);
          }
          if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
          throw err;
        }
      },
    });
    
  }
}
let subclaims010MethodRegistryLookup;

Registry.prototype.lookup = function lookup(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'subclaims010MethodRegistryLookup',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable2[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle0 = handleTable2[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.lookup"][Instruction::CallWasm] enter', {
          funcName: '[method]registry.lookup',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => subclaims010MethodRegistryLookup(handle0, toInt32(arg1)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant5;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant5 = undefined;
            break;
          }
          case 1: {
            var ptr2 = dataView(memory0).getUint32(ret + 8, true);
            var len2 = dataView(memory0).getUint32(ret + 12, true);
            var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
            let variant4;
            switch (dataView(memory0).getUint8(ret + 16, true)) {
              case 0: {
                variant4 = undefined;
                break;
              }
              case 1: {
                var ptr3 = dataView(memory0).getUint32(ret + 20, true);
                var len3 = dataView(memory0).getUint32(ret + 24, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant4 = result3;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            variant5 = {
              key: dataView(memory0).getInt32(ret + 4, true),
              title: result2,
              logicalModel: variant4,
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for option');
          }
        }
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.lookup"][Instruction::Return]', {
          funcName: '[method]registry.lookup',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([variant5]);
        const retCopy = variant5;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn6(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let subclaims010MethodRegistryRegister;

Registry.prototype.register = function register(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'subclaims010MethodRegistryRegister',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable2[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle0 = handleTable2[(handle1 << 1) + 1] & ~T_FLAG;
        
        var {key: v2_0, title: v2_1, logicalModel: v2_2 } = arg1;
        
        var encodeRes = _utf8AllocateAndEncode(v2_1, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        var variant5 = v2_2;
        let variant5_0;
        let variant5_1;
        let variant5_2;
        if (variant5 === null || variant5=== undefined) {
          variant5_0 = 0;
          variant5_1 = 0;
          variant5_2 = 0;
        } else {
          const e = variant5;
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr4= encodeRes.ptr;
          var len4 = encodeRes.len;
          
          variant5_0 = 1;
          variant5_1 = ptr4;
          variant5_2 = len4;
        }
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.register"][Instruction::CallWasm] enter', {
          funcName: '[method]registry.register',
          paramCount: 7,
          async: false,
          postReturn: false,
        });
        
        let ret;
        
        try {
          _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => subclaims010MethodRegistryRegister(handle0, toInt32(v2_0), ptr3, len3, variant5_0, variant5_1, variant5_2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.register"][Instruction::Return]', {
          funcName: '[method]registry.register',
          paramCount: 0,
          async: false,
          postReturn: false
        });
        task.resolve([ret]);
        task.exit();
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let subclaims010MethodRegistryAll;

Registry.prototype.all = function all() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'subclaims010MethodRegistryAll',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable2[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle0 = handleTable2[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.all"][Instruction::CallWasm] enter', {
          funcName: '[method]registry.all',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => subclaims010MethodRegistryAll(handle0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var len5 = dataView(memory0).getUint32(ret + 4, true);
        var base5 = dataView(memory0).getUint32(ret + 0, true);
        if (base5 % 4 !== 0) throw new TypeError(`list pointer [${base5}] is not aligned to 4`);
        var result5 = [];
        for (let i = 0; i < len5; i++) {
          const base = base5 + i * 24;
          var ptr2 = dataView(memory0).getUint32(base + 4, true);
          var len2 = dataView(memory0).getUint32(base + 8, true);
          var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
          let variant4;
          switch (dataView(memory0).getUint8(base + 12, true)) {
            case 0: {
              variant4 = undefined;
              break;
            }
            case 1: {
              var ptr3 = dataView(memory0).getUint32(base + 16, true);
              var len3 = dataView(memory0).getUint32(base + 20, true);
              var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
              variant4 = result3;
              break;
            }
            default: {
              throw new TypeError('invalid variant discriminant for option');
            }
          }
          result5.push({
            key: dataView(memory0).getInt32(base + 0, true),
            title: result2,
            logicalModel: variant4,
          });
        }
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.all"][Instruction::Return]', {
          funcName: '[method]registry.all',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result5]);
        const retCopy = result5;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn7(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let subclaims010MethodRegistrySubclaimFor;

Registry.prototype.subclaimFor = function subclaimFor(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'subclaims010MethodRegistrySubclaimFor',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable2[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle0 = handleTable2[(handle1 << 1) + 1] & ~T_FLAG;
        
        var val2 = arg1;
        let enum2;
        switch (val2) {
          case 'dvc-min': {
            enum2 = 0;
            break;
          }
          case 'vhl': {
            enum2 = 1;
            break;
          }
          default: {
            if ((arg1) instanceof Error) {
              console.error(arg1);
            }
            
            throw new TypeError(`"${val2}" is not one of the cases of payload-kind`);
          }
        }
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.subclaim-for"][Instruction::CallWasm] enter', {
          funcName: '[method]registry.subclaim-for',
          paramCount: 2,
          async: false,
          postReturn: false,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => subclaims010MethodRegistrySubclaimFor(handle0, enum2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant3;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant3 = undefined;
            break;
          }
          case 1: {
            variant3 = dataView(memory0).getInt32(ret + 4, true);
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for option');
          }
        }
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.subclaim-for"][Instruction::Return]', {
          funcName: '[method]registry.subclaim-for',
          paramCount: 1,
          async: false,
          postReturn: false
        });
        task.resolve([variant3]);
        task.exit();
        return variant3;
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let subclaims010MethodRegistrySetSubclaimFor;

Registry.prototype.setSubclaimFor = function setSubclaimFor(arg1, arg2) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'subclaims010MethodRegistrySetSubclaimFor',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (null!== null) {
    task.setReturnMemoryIdx(null);
    task.setReturnMemory((() => null)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable2[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle0 = handleTable2[(handle1 << 1) + 1] & ~T_FLAG;
        
        var val2 = arg1;
        let enum2;
        switch (val2) {
          case 'dvc-min': {
            enum2 = 0;
            break;
          }
          case 'vhl': {
            enum2 = 1;
            break;
          }
          default: {
            if ((arg1) instanceof Error) {
              console.error(arg1);
            }
            
            throw new TypeError(`"${val2}" is not one of the cases of payload-kind`);
          }
        }
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.set-subclaim-for"][Instruction::CallWasm] enter', {
          funcName: '[method]registry.set-subclaim-for',
          paramCount: 3,
          async: false,
          postReturn: false,
        });
        
        let ret;
        
        try {
          _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => subclaims010MethodRegistrySetSubclaimFor(handle0, enum2, toInt32(arg2)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        _debugLog('[iface="who:smart-trust/subclaims@0.1.0", function="[method]registry.set-subclaim-for"][Instruction::Return]', {
          funcName: '[method]registry.set-subclaim-for',
          paramCount: 0,
          async: false,
          postReturn: false
        });
        task.resolve([ret]);
        task.exit();
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let dvc010Validate;

function validate(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'dvc010Validate',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var ptr0 = realloc0(0, 0, 4, 132);
        var {n: v1_0, dob: v1_1, s: v1_2, nt: v1_3, id: v1_4, dt: v1_5, gn: v1_6, vx: v1_7, v: v1_8, extensions: v1_9 } = arg0;
        
        var encodeRes = _utf8AllocateAndEncode(v1_0, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 4, len2, true);
        dataView(memory0).setUint32(ptr0 + 0, ptr2, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_1, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 12, len3, true);
        dataView(memory0).setUint32(ptr0 + 8, ptr3, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_2, realloc0, memory0);
        var ptr4= encodeRes.ptr;
        var len4 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 20, len4, true);
        dataView(memory0).setUint32(ptr0 + 16, ptr4, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_3, realloc0, memory0);
        var ptr5= encodeRes.ptr;
        var len5 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 28, len5, true);
        dataView(memory0).setUint32(ptr0 + 24, ptr5, true);
        var variant7 = v1_4;
        if (variant7 === null || variant7=== undefined) {
          dataView(memory0).setInt8(ptr0 + 32, 0, true);
        } else {
          const e = variant7;
          dataView(memory0).setInt8(ptr0 + 32, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr6= encodeRes.ptr;
          var len6 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 40, len6, true);
          dataView(memory0).setUint32(ptr0 + 36, ptr6, true);
        }
        var variant9 = v1_5;
        if (variant9 === null || variant9=== undefined) {
          dataView(memory0).setInt8(ptr0 + 44, 0, true);
        } else {
          const e = variant9;
          dataView(memory0).setInt8(ptr0 + 44, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr8= encodeRes.ptr;
          var len8 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 52, len8, true);
          dataView(memory0).setUint32(ptr0 + 48, ptr8, true);
        }
        var variant11 = v1_6;
        if (variant11 === null || variant11=== undefined) {
          dataView(memory0).setInt8(ptr0 + 56, 0, true);
        } else {
          const e = variant11;
          dataView(memory0).setInt8(ptr0 + 56, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr10= encodeRes.ptr;
          var len10 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 64, len10, true);
          dataView(memory0).setUint32(ptr0 + 60, ptr10, true);
        }
        var {vp: v12_0, dv: v12_1, cn: v12_2, is: v12_3, bo: v12_4 } = v1_7;
        
        var encodeRes = _utf8AllocateAndEncode(v12_0, realloc0, memory0);
        var ptr13= encodeRes.ptr;
        var len13 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 72, len13, true);
        dataView(memory0).setUint32(ptr0 + 68, ptr13, true);
        
        var encodeRes = _utf8AllocateAndEncode(v12_1, realloc0, memory0);
        var ptr14= encodeRes.ptr;
        var len14 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 80, len14, true);
        dataView(memory0).setUint32(ptr0 + 76, ptr14, true);
        var variant16 = v12_2;
        if (variant16 === null || variant16=== undefined) {
          dataView(memory0).setInt8(ptr0 + 84, 0, true);
        } else {
          const e = variant16;
          dataView(memory0).setInt8(ptr0 + 84, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr15= encodeRes.ptr;
          var len15 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 92, len15, true);
          dataView(memory0).setUint32(ptr0 + 88, ptr15, true);
        }
        var variant18 = v12_3;
        if (variant18 === null || variant18=== undefined) {
          dataView(memory0).setInt8(ptr0 + 96, 0, true);
        } else {
          const e = variant18;
          dataView(memory0).setInt8(ptr0 + 96, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr17= encodeRes.ptr;
          var len17 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 104, len17, true);
          dataView(memory0).setUint32(ptr0 + 100, ptr17, true);
        }
        
        var encodeRes = _utf8AllocateAndEncode(v12_4, realloc0, memory0);
        var ptr19= encodeRes.ptr;
        var len19 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 112, len19, true);
        dataView(memory0).setUint32(ptr0 + 108, ptr19, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_8, realloc0, memory0);
        var ptr20= encodeRes.ptr;
        var len20 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 120, len20, true);
        dataView(memory0).setUint32(ptr0 + 116, ptr20, true);
        var vec24 = v1_9;
        var len24 = vec24.length;
        var result24 = realloc0(0, 0, 4, len24 * 16);
        for (let i = 0; i < vec24.length; i++) {
          const e = vec24[i];
          const base = result24 + i * 16;var [tuple21_0, tuple21_1] = e;
          
          var encodeRes = _utf8AllocateAndEncode(tuple21_0, realloc0, memory0);
          var ptr22= encodeRes.ptr;
          var len22 = encodeRes.len;
          
          dataView(memory0).setUint32(base + 4, len22, true);
          dataView(memory0).setUint32(base + 0, ptr22, true);
          var val23 = tuple21_1;
          var len23 = Array.isArray(val23) ? val23.length : val23.byteLength;
          var ptr23 = realloc0(0, 0, 1, len23 * 1);
          
          let valData23;
          const valLenBytes23 = len23 * 1;
          if (Array.isArray(val23)) {
            // Regular array likely containing numbers, write values to memory
            let offset = 0;
            const dv23 = new DataView(memory0.buffer);
            for (const v of val23) {
              _requireValidNumericPrimitive.bind(null, 'u8')(v);
              dv23.setUint8(ptr23+ offset, v, true);
              offset += 1;
            }
          } else {
            // TypedArray / ArrayBuffer-like, direct copy
            valData23 = new Uint8Array(val23.buffer || val23, val23.byteOffset, valLenBytes23);
            const out23 = new Uint8Array(memory0.buffer, ptr23, valLenBytes23);
            out23.set(valData23);
          }
          
          dataView(memory0).setUint32(base + 12, len23, true);
          dataView(memory0).setUint32(base + 8, ptr23, true);
        }
        dataView(memory0).setUint32(ptr0 + 128, len24, true);
        dataView(memory0).setUint32(ptr0 + 124, result24, true);
        _debugLog('[iface="who:smart-trust/dvc@0.1.0", function="validate"][Instruction::CallWasm] enter', {
          funcName: 'validate',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => dvc010Validate(ptr0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var len29 = dataView(memory0).getUint32(ret + 4, true);
        var base29 = dataView(memory0).getUint32(ret + 0, true);
        if (base29 % 4 !== 0) throw new TypeError(`list pointer [${base29}] is not aligned to 4`);
        var result29 = [];
        for (let i = 0; i < len29; i++) {
          const base = base29 + i * 28;
          var ptr25 = dataView(memory0).getUint32(base + 0, true);
          var len25 = dataView(memory0).getUint32(base + 4, true);
          var result25 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr25, len25));
          var ptr26 = dataView(memory0).getUint32(base + 8, true);
          var len26 = dataView(memory0).getUint32(base + 12, true);
          var result26 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr26, len26));
          var ptr27 = dataView(memory0).getUint32(base + 16, true);
          var len27 = dataView(memory0).getUint32(base + 20, true);
          var result27 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr27, len27));
          let enum28;
          switch (dataView(memory0).getUint8(base + 24, true)) {
            case 0: {
              enum28 = 'blocking';
              break;
            }
            case 1: {
              enum28 = 'warning';
              break;
            }
            case 2: {
              enum28 = 'information';
              break;
            }
            default: {
              throw new TypeError('invalid discriminant specified for Severity');
            }
          }
          result29.push({
            path: result25,
            code: result26,
            message: result27,
            severity: enum28,
          });
        }
        _debugLog('[iface="who:smart-trust/dvc@0.1.0", function="validate"][Instruction::Return]', {
          funcName: 'validate',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result29]);
        const retCopy = result29;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn8(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let dvc010ToCbor;

function toCbor(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'dvc010ToCbor',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var ptr0 = realloc0(0, 0, 4, 132);
        var {n: v1_0, dob: v1_1, s: v1_2, nt: v1_3, id: v1_4, dt: v1_5, gn: v1_6, vx: v1_7, v: v1_8, extensions: v1_9 } = arg0;
        
        var encodeRes = _utf8AllocateAndEncode(v1_0, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 4, len2, true);
        dataView(memory0).setUint32(ptr0 + 0, ptr2, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_1, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 12, len3, true);
        dataView(memory0).setUint32(ptr0 + 8, ptr3, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_2, realloc0, memory0);
        var ptr4= encodeRes.ptr;
        var len4 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 20, len4, true);
        dataView(memory0).setUint32(ptr0 + 16, ptr4, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_3, realloc0, memory0);
        var ptr5= encodeRes.ptr;
        var len5 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 28, len5, true);
        dataView(memory0).setUint32(ptr0 + 24, ptr5, true);
        var variant7 = v1_4;
        if (variant7 === null || variant7=== undefined) {
          dataView(memory0).setInt8(ptr0 + 32, 0, true);
        } else {
          const e = variant7;
          dataView(memory0).setInt8(ptr0 + 32, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr6= encodeRes.ptr;
          var len6 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 40, len6, true);
          dataView(memory0).setUint32(ptr0 + 36, ptr6, true);
        }
        var variant9 = v1_5;
        if (variant9 === null || variant9=== undefined) {
          dataView(memory0).setInt8(ptr0 + 44, 0, true);
        } else {
          const e = variant9;
          dataView(memory0).setInt8(ptr0 + 44, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr8= encodeRes.ptr;
          var len8 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 52, len8, true);
          dataView(memory0).setUint32(ptr0 + 48, ptr8, true);
        }
        var variant11 = v1_6;
        if (variant11 === null || variant11=== undefined) {
          dataView(memory0).setInt8(ptr0 + 56, 0, true);
        } else {
          const e = variant11;
          dataView(memory0).setInt8(ptr0 + 56, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr10= encodeRes.ptr;
          var len10 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 64, len10, true);
          dataView(memory0).setUint32(ptr0 + 60, ptr10, true);
        }
        var {vp: v12_0, dv: v12_1, cn: v12_2, is: v12_3, bo: v12_4 } = v1_7;
        
        var encodeRes = _utf8AllocateAndEncode(v12_0, realloc0, memory0);
        var ptr13= encodeRes.ptr;
        var len13 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 72, len13, true);
        dataView(memory0).setUint32(ptr0 + 68, ptr13, true);
        
        var encodeRes = _utf8AllocateAndEncode(v12_1, realloc0, memory0);
        var ptr14= encodeRes.ptr;
        var len14 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 80, len14, true);
        dataView(memory0).setUint32(ptr0 + 76, ptr14, true);
        var variant16 = v12_2;
        if (variant16 === null || variant16=== undefined) {
          dataView(memory0).setInt8(ptr0 + 84, 0, true);
        } else {
          const e = variant16;
          dataView(memory0).setInt8(ptr0 + 84, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr15= encodeRes.ptr;
          var len15 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 92, len15, true);
          dataView(memory0).setUint32(ptr0 + 88, ptr15, true);
        }
        var variant18 = v12_3;
        if (variant18 === null || variant18=== undefined) {
          dataView(memory0).setInt8(ptr0 + 96, 0, true);
        } else {
          const e = variant18;
          dataView(memory0).setInt8(ptr0 + 96, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr17= encodeRes.ptr;
          var len17 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 104, len17, true);
          dataView(memory0).setUint32(ptr0 + 100, ptr17, true);
        }
        
        var encodeRes = _utf8AllocateAndEncode(v12_4, realloc0, memory0);
        var ptr19= encodeRes.ptr;
        var len19 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 112, len19, true);
        dataView(memory0).setUint32(ptr0 + 108, ptr19, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_8, realloc0, memory0);
        var ptr20= encodeRes.ptr;
        var len20 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 120, len20, true);
        dataView(memory0).setUint32(ptr0 + 116, ptr20, true);
        var vec24 = v1_9;
        var len24 = vec24.length;
        var result24 = realloc0(0, 0, 4, len24 * 16);
        for (let i = 0; i < vec24.length; i++) {
          const e = vec24[i];
          const base = result24 + i * 16;var [tuple21_0, tuple21_1] = e;
          
          var encodeRes = _utf8AllocateAndEncode(tuple21_0, realloc0, memory0);
          var ptr22= encodeRes.ptr;
          var len22 = encodeRes.len;
          
          dataView(memory0).setUint32(base + 4, len22, true);
          dataView(memory0).setUint32(base + 0, ptr22, true);
          var val23 = tuple21_1;
          var len23 = Array.isArray(val23) ? val23.length : val23.byteLength;
          var ptr23 = realloc0(0, 0, 1, len23 * 1);
          
          let valData23;
          const valLenBytes23 = len23 * 1;
          if (Array.isArray(val23)) {
            // Regular array likely containing numbers, write values to memory
            let offset = 0;
            const dv23 = new DataView(memory0.buffer);
            for (const v of val23) {
              _requireValidNumericPrimitive.bind(null, 'u8')(v);
              dv23.setUint8(ptr23+ offset, v, true);
              offset += 1;
            }
          } else {
            // TypedArray / ArrayBuffer-like, direct copy
            valData23 = new Uint8Array(val23.buffer || val23, val23.byteOffset, valLenBytes23);
            const out23 = new Uint8Array(memory0.buffer, ptr23, valLenBytes23);
            out23.set(valData23);
          }
          
          dataView(memory0).setUint32(base + 12, len23, true);
          dataView(memory0).setUint32(base + 8, ptr23, true);
        }
        dataView(memory0).setUint32(ptr0 + 128, len24, true);
        dataView(memory0).setUint32(ptr0 + 124, result24, true);
        _debugLog('[iface="who:smart-trust/dvc@0.1.0", function="to-cbor"][Instruction::CallWasm] enter', {
          funcName: 'to-cbor',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => dvc010ToCbor(ptr0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr25 = dataView(memory0).getUint32(ret + 0, true);
        var len25 = dataView(memory0).getUint32(ret + 4, true);
        if (ptr25 % 1 !== 0) throw new TypeError(`list pointer [${ptr25}] is not aligned to 1`);
        var result25 = new Uint8Array(memory0.buffer.slice(ptr25, ptr25 + len25 * 1));
        _debugLog('[iface="who:smart-trust/dvc@0.1.0", function="to-cbor"][Instruction::Return]', {
          funcName: 'to-cbor',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result25]);
        const retCopy = result25;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let dvc010FromCbor;

function fromCbor(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'dvc010FromCbor',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        var {maxInputChars: v1_0, maxInflatedBytes: v1_1, maxDepth: v1_2, maxItems: v1_3, maxStringBytes: v1_4 } = arg1;
        _debugLog('[iface="who:smart-trust/dvc@0.1.0", function="from-cbor"][Instruction::CallWasm] enter', {
          funcName: 'from-cbor',
          paramCount: 7,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => dvc010FromCbor(ptr0, len0, toUint32(v1_0), toUint32(v1_1), toUint32(v1_2), toUint32(v1_3), toUint32(v1_4)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant28;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr2 = dataView(memory0).getUint32(ret + 4, true);
            var len2 = dataView(memory0).getUint32(ret + 8, true);
            var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
            var ptr3 = dataView(memory0).getUint32(ret + 12, true);
            var len3 = dataView(memory0).getUint32(ret + 16, true);
            var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
            var ptr4 = dataView(memory0).getUint32(ret + 20, true);
            var len4 = dataView(memory0).getUint32(ret + 24, true);
            var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
            var ptr5 = dataView(memory0).getUint32(ret + 28, true);
            var len5 = dataView(memory0).getUint32(ret + 32, true);
            var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
            let variant7;
            switch (dataView(memory0).getUint8(ret + 36, true)) {
              case 0: {
                variant7 = undefined;
                break;
              }
              case 1: {
                var ptr6 = dataView(memory0).getUint32(ret + 40, true);
                var len6 = dataView(memory0).getUint32(ret + 44, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant7 = result6;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant9;
            switch (dataView(memory0).getUint8(ret + 48, true)) {
              case 0: {
                variant9 = undefined;
                break;
              }
              case 1: {
                var ptr8 = dataView(memory0).getUint32(ret + 52, true);
                var len8 = dataView(memory0).getUint32(ret + 56, true);
                var result8 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr8, len8));
                variant9 = result8;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant11;
            switch (dataView(memory0).getUint8(ret + 60, true)) {
              case 0: {
                variant11 = undefined;
                break;
              }
              case 1: {
                var ptr10 = dataView(memory0).getUint32(ret + 64, true);
                var len10 = dataView(memory0).getUint32(ret + 68, true);
                var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                variant11 = result10;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var ptr12 = dataView(memory0).getUint32(ret + 72, true);
            var len12 = dataView(memory0).getUint32(ret + 76, true);
            var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
            var ptr13 = dataView(memory0).getUint32(ret + 80, true);
            var len13 = dataView(memory0).getUint32(ret + 84, true);
            var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
            let variant15;
            switch (dataView(memory0).getUint8(ret + 88, true)) {
              case 0: {
                variant15 = undefined;
                break;
              }
              case 1: {
                var ptr14 = dataView(memory0).getUint32(ret + 92, true);
                var len14 = dataView(memory0).getUint32(ret + 96, true);
                var result14 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr14, len14));
                variant15 = result14;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant17;
            switch (dataView(memory0).getUint8(ret + 100, true)) {
              case 0: {
                variant17 = undefined;
                break;
              }
              case 1: {
                var ptr16 = dataView(memory0).getUint32(ret + 104, true);
                var len16 = dataView(memory0).getUint32(ret + 108, true);
                var result16 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr16, len16));
                variant17 = result16;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var ptr18 = dataView(memory0).getUint32(ret + 112, true);
            var len18 = dataView(memory0).getUint32(ret + 116, true);
            var result18 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr18, len18));
            var ptr19 = dataView(memory0).getUint32(ret + 120, true);
            var len19 = dataView(memory0).getUint32(ret + 124, true);
            var result19 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr19, len19));
            var len22 = dataView(memory0).getUint32(ret + 132, true);
            var base22 = dataView(memory0).getUint32(ret + 128, true);
            if (base22 % 4 !== 0) throw new TypeError(`list pointer [${base22}] is not aligned to 4`);
            var result22 = [];
            for (let i = 0; i < len22; i++) {
              const base = base22 + i * 16;
              var ptr20 = dataView(memory0).getUint32(base + 0, true);
              var len20 = dataView(memory0).getUint32(base + 4, true);
              var result20 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr20, len20));
              var ptr21 = dataView(memory0).getUint32(base + 8, true);
              var len21 = dataView(memory0).getUint32(base + 12, true);
              if (ptr21 % 1 !== 0) throw new TypeError(`list pointer [${ptr21}] is not aligned to 1`);
              var result21 = new Uint8Array(memory0.buffer.slice(ptr21, ptr21 + len21 * 1));
              result22.push([result20, result21]);
            }
            variant28= {
              tag: 'ok',
              val: {
                n: result2,
                dob: result3,
                s: result4,
                nt: result5,
                id: variant7,
                dt: variant9,
                gn: variant11,
                vx: {
                  vp: result12,
                  dv: result13,
                  cn: variant15,
                  is: variant17,
                  bo: result18,
                },
                v: result19,
                extensions: result22,
              }
            };
            break;
          }
          case 1: {
            var len27 = dataView(memory0).getUint32(ret + 8, true);
            var base27 = dataView(memory0).getUint32(ret + 4, true);
            if (base27 % 4 !== 0) throw new TypeError(`list pointer [${base27}] is not aligned to 4`);
            var result27 = [];
            for (let i = 0; i < len27; i++) {
              const base = base27 + i * 28;
              var ptr23 = dataView(memory0).getUint32(base + 0, true);
              var len23 = dataView(memory0).getUint32(base + 4, true);
              var result23 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr23, len23));
              var ptr24 = dataView(memory0).getUint32(base + 8, true);
              var len24 = dataView(memory0).getUint32(base + 12, true);
              var result24 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr24, len24));
              var ptr25 = dataView(memory0).getUint32(base + 16, true);
              var len25 = dataView(memory0).getUint32(base + 20, true);
              var result25 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr25, len25));
              let enum26;
              switch (dataView(memory0).getUint8(base + 24, true)) {
                case 0: {
                  enum26 = 'blocking';
                  break;
                }
                case 1: {
                  enum26 = 'warning';
                  break;
                }
                case 2: {
                  enum26 = 'information';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Severity');
                }
              }
              result27.push({
                path: result23,
                code: result24,
                message: result25,
                severity: enum26,
              });
            }
            variant28= {
              tag: 'err',
              val: result27
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/dvc@0.1.0", function="from-cbor"][Instruction::Return]', {
          funcName: 'from-cbor',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant28;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn9(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010Validate;

function validate$1(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010Validate',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var ptr0 = realloc0(0, 0, 8, 96);
        var {url: v1_0, key: v1_1, exp: v1_2, flag: v1_3, label: v1_4, v: v1_5, fhirBaseUrl: v1_6 } = arg0;
        
        var encodeRes = _utf8AllocateAndEncode(v1_0, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 4, len2, true);
        dataView(memory0).setUint32(ptr0 + 0, ptr2, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_1, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 12, len3, true);
        dataView(memory0).setUint32(ptr0 + 8, ptr3, true);
        var variant4 = v1_2;
        if (variant4 === null || variant4=== undefined) {
          dataView(memory0).setInt8(ptr0 + 16, 0, true);
        } else {
          const e = variant4;
          dataView(memory0).setInt8(ptr0 + 16, 1, true);
          dataView(memory0).setBigInt64(ptr0 + 24, toInt64(e), true);
        }
        var variant6 = v1_3;
        if (variant6 === null || variant6=== undefined) {
          dataView(memory0).setInt8(ptr0 + 32, 0, true);
        } else {
          const e = variant6;
          dataView(memory0).setInt8(ptr0 + 32, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr5= encodeRes.ptr;
          var len5 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 40, len5, true);
          dataView(memory0).setUint32(ptr0 + 36, ptr5, true);
        }
        var variant8 = v1_4;
        if (variant8 === null || variant8=== undefined) {
          dataView(memory0).setInt8(ptr0 + 44, 0, true);
        } else {
          const e = variant8;
          dataView(memory0).setInt8(ptr0 + 44, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr7= encodeRes.ptr;
          var len7 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 52, len7, true);
          dataView(memory0).setUint32(ptr0 + 48, ptr7, true);
        }
        var variant9 = v1_5;
        if (variant9 === null || variant9=== undefined) {
          dataView(memory0).setInt8(ptr0 + 56, 0, true);
        } else {
          const e = variant9;
          dataView(memory0).setInt8(ptr0 + 56, 1, true);
          dataView(memory0).setInt32(ptr0 + 60, toInt32(e), true);
        }
        var variant11 = v1_6;
        if (variant11 === null || variant11=== undefined) {
          dataView(memory0).setInt8(ptr0 + 64, 0, true);
        } else {
          const e = variant11;
          dataView(memory0).setInt8(ptr0 + 64, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr10= encodeRes.ptr;
          var len10 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 72, len10, true);
          dataView(memory0).setUint32(ptr0 + 68, ptr10, true);
        }
        var variant12 = arg1;
        if (variant12 === null || variant12=== undefined) {
          dataView(memory0).setInt8(ptr0 + 80, 0, true);
        } else {
          const e = variant12;
          dataView(memory0).setInt8(ptr0 + 80, 1, true);
          dataView(memory0).setBigInt64(ptr0 + 88, toInt64(e), true);
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="validate"][Instruction::CallWasm] enter', {
          funcName: 'validate',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010Validate(ptr0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var len17 = dataView(memory0).getUint32(ret + 4, true);
        var base17 = dataView(memory0).getUint32(ret + 0, true);
        if (base17 % 4 !== 0) throw new TypeError(`list pointer [${base17}] is not aligned to 4`);
        var result17 = [];
        for (let i = 0; i < len17; i++) {
          const base = base17 + i * 28;
          var ptr13 = dataView(memory0).getUint32(base + 0, true);
          var len13 = dataView(memory0).getUint32(base + 4, true);
          var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
          var ptr14 = dataView(memory0).getUint32(base + 8, true);
          var len14 = dataView(memory0).getUint32(base + 12, true);
          var result14 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr14, len14));
          var ptr15 = dataView(memory0).getUint32(base + 16, true);
          var len15 = dataView(memory0).getUint32(base + 20, true);
          var result15 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr15, len15));
          let enum16;
          switch (dataView(memory0).getUint8(base + 24, true)) {
            case 0: {
              enum16 = 'blocking';
              break;
            }
            case 1: {
              enum16 = 'warning';
              break;
            }
            case 2: {
              enum16 = 'information';
              break;
            }
            default: {
              throw new TypeError('invalid discriminant specified for Severity');
            }
          }
          result17.push({
            path: result13,
            code: result14,
            message: result15,
            severity: enum16,
          });
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="validate"][Instruction::Return]', {
          funcName: 'validate',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result17]);
        const retCopy = result17;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn8(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010ToVhlink;

function toVhlink(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010ToVhlink',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var ptr0 = realloc0(0, 0, 8, 80);
        var {url: v1_0, key: v1_1, exp: v1_2, flag: v1_3, label: v1_4, v: v1_5, fhirBaseUrl: v1_6 } = arg0;
        
        var encodeRes = _utf8AllocateAndEncode(v1_0, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 4, len2, true);
        dataView(memory0).setUint32(ptr0 + 0, ptr2, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_1, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 12, len3, true);
        dataView(memory0).setUint32(ptr0 + 8, ptr3, true);
        var variant4 = v1_2;
        if (variant4 === null || variant4=== undefined) {
          dataView(memory0).setInt8(ptr0 + 16, 0, true);
        } else {
          const e = variant4;
          dataView(memory0).setInt8(ptr0 + 16, 1, true);
          dataView(memory0).setBigInt64(ptr0 + 24, toInt64(e), true);
        }
        var variant6 = v1_3;
        if (variant6 === null || variant6=== undefined) {
          dataView(memory0).setInt8(ptr0 + 32, 0, true);
        } else {
          const e = variant6;
          dataView(memory0).setInt8(ptr0 + 32, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr5= encodeRes.ptr;
          var len5 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 40, len5, true);
          dataView(memory0).setUint32(ptr0 + 36, ptr5, true);
        }
        var variant8 = v1_4;
        if (variant8 === null || variant8=== undefined) {
          dataView(memory0).setInt8(ptr0 + 44, 0, true);
        } else {
          const e = variant8;
          dataView(memory0).setInt8(ptr0 + 44, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr7= encodeRes.ptr;
          var len7 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 52, len7, true);
          dataView(memory0).setUint32(ptr0 + 48, ptr7, true);
        }
        var variant9 = v1_5;
        if (variant9 === null || variant9=== undefined) {
          dataView(memory0).setInt8(ptr0 + 56, 0, true);
        } else {
          const e = variant9;
          dataView(memory0).setInt8(ptr0 + 56, 1, true);
          dataView(memory0).setInt32(ptr0 + 60, toInt32(e), true);
        }
        var variant11 = v1_6;
        if (variant11 === null || variant11=== undefined) {
          dataView(memory0).setInt8(ptr0 + 64, 0, true);
        } else {
          const e = variant11;
          dataView(memory0).setInt8(ptr0 + 64, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr10= encodeRes.ptr;
          var len10 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 72, len10, true);
          dataView(memory0).setUint32(ptr0 + 68, ptr10, true);
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="to-vhlink"][Instruction::CallWasm] enter', {
          funcName: 'to-vhlink',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010ToVhlink(ptr0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr12 = dataView(memory0).getUint32(ret + 0, true);
        var len12 = dataView(memory0).getUint32(ret + 4, true);
        var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="to-vhlink"][Instruction::Return]', {
          funcName: 'to-vhlink',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result12]);
        const retCopy = result12;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010FromVhlink;

function fromVhlink(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010FromVhlink',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="from-vhlink"][Instruction::CallWasm] enter', {
          funcName: 'from-vhlink',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010FromVhlink(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant21;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr1 = dataView(memory0).getUint32(ret + 8, true);
            var len1 = dataView(memory0).getUint32(ret + 12, true);
            var result1 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr1, len1));
            var ptr2 = dataView(memory0).getUint32(ret + 16, true);
            var len2 = dataView(memory0).getUint32(ret + 20, true);
            var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
            let variant3;
            switch (dataView(memory0).getUint8(ret + 24, true)) {
              case 0: {
                variant3 = undefined;
                break;
              }
              case 1: {
                variant3 = dataView(memory0).getBigInt64(ret + 32, true);
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant5;
            switch (dataView(memory0).getUint8(ret + 40, true)) {
              case 0: {
                variant5 = undefined;
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 44, true);
                var len4 = dataView(memory0).getUint32(ret + 48, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant5 = result4;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant7;
            switch (dataView(memory0).getUint8(ret + 52, true)) {
              case 0: {
                variant7 = undefined;
                break;
              }
              case 1: {
                var ptr6 = dataView(memory0).getUint32(ret + 56, true);
                var len6 = dataView(memory0).getUint32(ret + 60, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant7 = result6;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant8;
            switch (dataView(memory0).getUint8(ret + 64, true)) {
              case 0: {
                variant8 = undefined;
                break;
              }
              case 1: {
                variant8 = dataView(memory0).getInt32(ret + 68, true);
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant10;
            switch (dataView(memory0).getUint8(ret + 72, true)) {
              case 0: {
                variant10 = undefined;
                break;
              }
              case 1: {
                var ptr9 = dataView(memory0).getUint32(ret + 76, true);
                var len9 = dataView(memory0).getUint32(ret + 80, true);
                var result9 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr9, len9));
                variant10 = result9;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            variant21= {
              tag: 'ok',
              val: {
                url: result1,
                key: result2,
                exp: variant3,
                flag: variant5,
                label: variant7,
                v: variant8,
                fhirBaseUrl: variant10,
              }
            };
            break;
          }
          case 1: {
            let variant20;
            switch (dataView(memory0).getUint8(ret + 8, true)) {
              case 0: {
                var len15 = dataView(memory0).getUint32(ret + 16, true);
                var base15 = dataView(memory0).getUint32(ret + 12, true);
                if (base15 % 4 !== 0) throw new TypeError(`list pointer [${base15}] is not aligned to 4`);
                var result15 = [];
                for (let i = 0; i < len15; i++) {
                  const base = base15 + i * 28;
                  var ptr11 = dataView(memory0).getUint32(base + 0, true);
                  var len11 = dataView(memory0).getUint32(base + 4, true);
                  var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                  var ptr12 = dataView(memory0).getUint32(base + 8, true);
                  var len12 = dataView(memory0).getUint32(base + 12, true);
                  var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                  var ptr13 = dataView(memory0).getUint32(base + 16, true);
                  var len13 = dataView(memory0).getUint32(base + 20, true);
                  var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
                  let enum14;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum14 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum14 = 'warning';
                      break;
                    }
                    case 2: {
                      enum14 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result15.push({
                    path: result11,
                    code: result12,
                    message: result13,
                    severity: enum14,
                  });
                }
                variant20= {
                  tag: 'invalid',
                  val: result15
                };
                break;
              }
              case 1: {
                var ptr16 = dataView(memory0).getUint32(ret + 12, true);
                var len16 = dataView(memory0).getUint32(ret + 16, true);
                var result16 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr16, len16));
                variant20= {
                  tag: 'not-a-vhlink',
                  val: result16
                };
                break;
              }
              case 2: {
                var ptr17 = dataView(memory0).getUint32(ret + 12, true);
                var len17 = dataView(memory0).getUint32(ret + 16, true);
                var result17 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr17, len17));
                variant20= {
                  tag: 'manifest',
                  val: result17
                };
                break;
              }
              case 3: {
                var ptr18 = dataView(memory0).getUint32(ret + 12, true);
                var len18 = dataView(memory0).getUint32(ret + 16, true);
                var result18 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr18, len18));
                variant20= {
                  tag: 'decrypt-failed',
                  val: result18
                };
                break;
              }
              case 4: {
                var ptr19 = dataView(memory0).getUint32(ret + 12, true);
                var len19 = dataView(memory0).getUint32(ret + 16, true);
                var result19 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr19, len19));
                variant20= {
                  tag: 'unsupported',
                  val: result19
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for VhlError');
              }
            }
            variant21= {
              tag: 'err',
              val: variant20
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="from-vhlink"][Instruction::Return]', {
          funcName: 'from-vhlink',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant21;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn10(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010NewKey;

function newKey() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010NewKey',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="new-key"][Instruction::CallWasm] enter', {
          funcName: 'new-key',
          paramCount: 0,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010NewKey(),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr0 = dataView(memory0).getUint32(ret + 0, true);
        var len0 = dataView(memory0).getUint32(ret + 4, true);
        var result0 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr0, len0));
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="new-key"][Instruction::Return]', {
          funcName: 'new-key',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result0]);
        const retCopy = result0;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010BuildManifestRequest;

function buildManifestRequest(arg0, arg1, arg2, arg3) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010BuildManifestRequest',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var ptr0 = realloc0(0, 0, 8, 112);
        var {url: v1_0, key: v1_1, exp: v1_2, flag: v1_3, label: v1_4, v: v1_5, fhirBaseUrl: v1_6 } = arg0;
        
        var encodeRes = _utf8AllocateAndEncode(v1_0, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 4, len2, true);
        dataView(memory0).setUint32(ptr0 + 0, ptr2, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_1, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 12, len3, true);
        dataView(memory0).setUint32(ptr0 + 8, ptr3, true);
        var variant4 = v1_2;
        if (variant4 === null || variant4=== undefined) {
          dataView(memory0).setInt8(ptr0 + 16, 0, true);
        } else {
          const e = variant4;
          dataView(memory0).setInt8(ptr0 + 16, 1, true);
          dataView(memory0).setBigInt64(ptr0 + 24, toInt64(e), true);
        }
        var variant6 = v1_3;
        if (variant6 === null || variant6=== undefined) {
          dataView(memory0).setInt8(ptr0 + 32, 0, true);
        } else {
          const e = variant6;
          dataView(memory0).setInt8(ptr0 + 32, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr5= encodeRes.ptr;
          var len5 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 40, len5, true);
          dataView(memory0).setUint32(ptr0 + 36, ptr5, true);
        }
        var variant8 = v1_4;
        if (variant8 === null || variant8=== undefined) {
          dataView(memory0).setInt8(ptr0 + 44, 0, true);
        } else {
          const e = variant8;
          dataView(memory0).setInt8(ptr0 + 44, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr7= encodeRes.ptr;
          var len7 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 52, len7, true);
          dataView(memory0).setUint32(ptr0 + 48, ptr7, true);
        }
        var variant9 = v1_5;
        if (variant9 === null || variant9=== undefined) {
          dataView(memory0).setInt8(ptr0 + 56, 0, true);
        } else {
          const e = variant9;
          dataView(memory0).setInt8(ptr0 + 56, 1, true);
          dataView(memory0).setInt32(ptr0 + 60, toInt32(e), true);
        }
        var variant11 = v1_6;
        if (variant11 === null || variant11=== undefined) {
          dataView(memory0).setInt8(ptr0 + 64, 0, true);
        } else {
          const e = variant11;
          dataView(memory0).setInt8(ptr0 + 64, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr10= encodeRes.ptr;
          var len10 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 72, len10, true);
          dataView(memory0).setUint32(ptr0 + 68, ptr10, true);
        }
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr12= encodeRes.ptr;
        var len12 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 84, len12, true);
        dataView(memory0).setUint32(ptr0 + 80, ptr12, true);
        var variant14 = arg2;
        if (variant14 === null || variant14=== undefined) {
          dataView(memory0).setInt8(ptr0 + 88, 0, true);
        } else {
          const e = variant14;
          dataView(memory0).setInt8(ptr0 + 88, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr13= encodeRes.ptr;
          var len13 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 96, len13, true);
          dataView(memory0).setUint32(ptr0 + 92, ptr13, true);
        }
        var variant15 = arg3;
        if (variant15 === null || variant15=== undefined) {
          dataView(memory0).setInt8(ptr0 + 100, 0, true);
        } else {
          const e = variant15;
          dataView(memory0).setInt8(ptr0 + 100, 1, true);
          dataView(memory0).setInt32(ptr0 + 104, toUint32(e), true);
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="build-manifest-request"][Instruction::CallWasm] enter', {
          funcName: 'build-manifest-request',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010BuildManifestRequest(ptr0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant29;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr16 = dataView(memory0).getUint32(ret + 4, true);
            var len16 = dataView(memory0).getUint32(ret + 8, true);
            var result16 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr16, len16));
            var ptr17 = dataView(memory0).getUint32(ret + 12, true);
            var len17 = dataView(memory0).getUint32(ret + 16, true);
            var result17 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr17, len17));
            var ptr18 = dataView(memory0).getUint32(ret + 20, true);
            var len18 = dataView(memory0).getUint32(ret + 24, true);
            var result18 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr18, len18));
            variant29= {
              tag: 'ok',
              val: {
                url: result16,
                contentType: result17,
                body: result18,
              }
            };
            break;
          }
          case 1: {
            let variant28;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var len23 = dataView(memory0).getUint32(ret + 12, true);
                var base23 = dataView(memory0).getUint32(ret + 8, true);
                if (base23 % 4 !== 0) throw new TypeError(`list pointer [${base23}] is not aligned to 4`);
                var result23 = [];
                for (let i = 0; i < len23; i++) {
                  const base = base23 + i * 28;
                  var ptr19 = dataView(memory0).getUint32(base + 0, true);
                  var len19 = dataView(memory0).getUint32(base + 4, true);
                  var result19 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr19, len19));
                  var ptr20 = dataView(memory0).getUint32(base + 8, true);
                  var len20 = dataView(memory0).getUint32(base + 12, true);
                  var result20 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr20, len20));
                  var ptr21 = dataView(memory0).getUint32(base + 16, true);
                  var len21 = dataView(memory0).getUint32(base + 20, true);
                  var result21 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr21, len21));
                  let enum22;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum22 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum22 = 'warning';
                      break;
                    }
                    case 2: {
                      enum22 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result23.push({
                    path: result19,
                    code: result20,
                    message: result21,
                    severity: enum22,
                  });
                }
                variant28= {
                  tag: 'invalid',
                  val: result23
                };
                break;
              }
              case 1: {
                var ptr24 = dataView(memory0).getUint32(ret + 8, true);
                var len24 = dataView(memory0).getUint32(ret + 12, true);
                var result24 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr24, len24));
                variant28= {
                  tag: 'not-a-vhlink',
                  val: result24
                };
                break;
              }
              case 2: {
                var ptr25 = dataView(memory0).getUint32(ret + 8, true);
                var len25 = dataView(memory0).getUint32(ret + 12, true);
                var result25 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr25, len25));
                variant28= {
                  tag: 'manifest',
                  val: result25
                };
                break;
              }
              case 3: {
                var ptr26 = dataView(memory0).getUint32(ret + 8, true);
                var len26 = dataView(memory0).getUint32(ret + 12, true);
                var result26 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr26, len26));
                variant28= {
                  tag: 'decrypt-failed',
                  val: result26
                };
                break;
              }
              case 4: {
                var ptr27 = dataView(memory0).getUint32(ret + 8, true);
                var len27 = dataView(memory0).getUint32(ret + 12, true);
                var result27 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr27, len27));
                variant28= {
                  tag: 'unsupported',
                  val: result27
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for VhlError');
              }
            }
            variant29= {
              tag: 'err',
              val: variant28
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="build-manifest-request"][Instruction::Return]', {
          funcName: 'build-manifest-request',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant29;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn11(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010ParseManifest;

function parseManifest(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010ParseManifest',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="parse-manifest"][Instruction::CallWasm] enter', {
          funcName: 'parse-manifest',
          paramCount: 2,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010ParseManifest(ptr0, len0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant20;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var len9 = dataView(memory0).getUint32(ret + 8, true);
            var base9 = dataView(memory0).getUint32(ret + 4, true);
            if (base9 % 4 !== 0) throw new TypeError(`list pointer [${base9}] is not aligned to 4`);
            var result9 = [];
            for (let i = 0; i < len9; i++) {
              const base = base9 + i * 48;
              let variant2;
              switch (dataView(memory0).getUint8(base + 0, true)) {
                case 0: {
                  variant2 = undefined;
                  break;
                }
                case 1: {
                  var ptr1 = dataView(memory0).getUint32(base + 4, true);
                  var len1 = dataView(memory0).getUint32(base + 8, true);
                  var result1 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr1, len1));
                  variant2 = result1;
                  break;
                }
                default: {
                  throw new TypeError('invalid variant discriminant for option');
                }
              }
              let variant4;
              switch (dataView(memory0).getUint8(base + 12, true)) {
                case 0: {
                  variant4 = undefined;
                  break;
                }
                case 1: {
                  var ptr3 = dataView(memory0).getUint32(base + 16, true);
                  var len3 = dataView(memory0).getUint32(base + 20, true);
                  var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                  variant4 = result3;
                  break;
                }
                default: {
                  throw new TypeError('invalid variant discriminant for option');
                }
              }
              let variant6;
              switch (dataView(memory0).getUint8(base + 24, true)) {
                case 0: {
                  variant6 = undefined;
                  break;
                }
                case 1: {
                  var ptr5 = dataView(memory0).getUint32(base + 28, true);
                  var len5 = dataView(memory0).getUint32(base + 32, true);
                  var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                  variant6 = result5;
                  break;
                }
                default: {
                  throw new TypeError('invalid variant discriminant for option');
                }
              }
              let variant8;
              switch (dataView(memory0).getUint8(base + 36, true)) {
                case 0: {
                  variant8 = undefined;
                  break;
                }
                case 1: {
                  var ptr7 = dataView(memory0).getUint32(base + 40, true);
                  var len7 = dataView(memory0).getUint32(base + 44, true);
                  var result7 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr7, len7));
                  variant8 = result7;
                  break;
                }
                default: {
                  throw new TypeError('invalid variant discriminant for option');
                }
              }
              result9.push({
                url: variant2,
                reference: variant4,
                contentType: variant6,
                title: variant8,
              });
            }
            variant20= {
              tag: 'ok',
              val: result9
            };
            break;
          }
          case 1: {
            let variant19;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var len14 = dataView(memory0).getUint32(ret + 12, true);
                var base14 = dataView(memory0).getUint32(ret + 8, true);
                if (base14 % 4 !== 0) throw new TypeError(`list pointer [${base14}] is not aligned to 4`);
                var result14 = [];
                for (let i = 0; i < len14; i++) {
                  const base = base14 + i * 28;
                  var ptr10 = dataView(memory0).getUint32(base + 0, true);
                  var len10 = dataView(memory0).getUint32(base + 4, true);
                  var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                  var ptr11 = dataView(memory0).getUint32(base + 8, true);
                  var len11 = dataView(memory0).getUint32(base + 12, true);
                  var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                  var ptr12 = dataView(memory0).getUint32(base + 16, true);
                  var len12 = dataView(memory0).getUint32(base + 20, true);
                  var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                  let enum13;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum13 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum13 = 'warning';
                      break;
                    }
                    case 2: {
                      enum13 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result14.push({
                    path: result10,
                    code: result11,
                    message: result12,
                    severity: enum13,
                  });
                }
                variant19= {
                  tag: 'invalid',
                  val: result14
                };
                break;
              }
              case 1: {
                var ptr15 = dataView(memory0).getUint32(ret + 8, true);
                var len15 = dataView(memory0).getUint32(ret + 12, true);
                var result15 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr15, len15));
                variant19= {
                  tag: 'not-a-vhlink',
                  val: result15
                };
                break;
              }
              case 2: {
                var ptr16 = dataView(memory0).getUint32(ret + 8, true);
                var len16 = dataView(memory0).getUint32(ret + 12, true);
                var result16 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr16, len16));
                variant19= {
                  tag: 'manifest',
                  val: result16
                };
                break;
              }
              case 3: {
                var ptr17 = dataView(memory0).getUint32(ret + 8, true);
                var len17 = dataView(memory0).getUint32(ret + 12, true);
                var result17 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr17, len17));
                variant19= {
                  tag: 'decrypt-failed',
                  val: result17
                };
                break;
              }
              case 4: {
                var ptr18 = dataView(memory0).getUint32(ret + 8, true);
                var len18 = dataView(memory0).getUint32(ret + 12, true);
                var result18 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr18, len18));
                variant19= {
                  tag: 'unsupported',
                  val: result18
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for VhlError');
              }
            }
            variant20= {
              tag: 'err',
              val: variant19
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="parse-manifest"][Instruction::Return]', {
          funcName: 'parse-manifest',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant20;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn12(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010Encrypt;

function encrypt(arg0, arg1, arg2) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010Encrypt',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr1= encodeRes.ptr;
        var len1 = encodeRes.len;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg2, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="encrypt"][Instruction::CallWasm] enter', {
          funcName: 'encrypt',
          paramCount: 6,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010Encrypt(ptr0, len0, ptr1, len1, ptr2, len2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant14;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr3 = dataView(memory0).getUint32(ret + 4, true);
            var len3 = dataView(memory0).getUint32(ret + 8, true);
            var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
            variant14= {
              tag: 'ok',
              val: result3
            };
            break;
          }
          case 1: {
            let variant13;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var len8 = dataView(memory0).getUint32(ret + 12, true);
                var base8 = dataView(memory0).getUint32(ret + 8, true);
                if (base8 % 4 !== 0) throw new TypeError(`list pointer [${base8}] is not aligned to 4`);
                var result8 = [];
                for (let i = 0; i < len8; i++) {
                  const base = base8 + i * 28;
                  var ptr4 = dataView(memory0).getUint32(base + 0, true);
                  var len4 = dataView(memory0).getUint32(base + 4, true);
                  var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                  var ptr5 = dataView(memory0).getUint32(base + 8, true);
                  var len5 = dataView(memory0).getUint32(base + 12, true);
                  var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                  var ptr6 = dataView(memory0).getUint32(base + 16, true);
                  var len6 = dataView(memory0).getUint32(base + 20, true);
                  var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                  let enum7;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum7 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum7 = 'warning';
                      break;
                    }
                    case 2: {
                      enum7 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result8.push({
                    path: result4,
                    code: result5,
                    message: result6,
                    severity: enum7,
                  });
                }
                variant13= {
                  tag: 'invalid',
                  val: result8
                };
                break;
              }
              case 1: {
                var ptr9 = dataView(memory0).getUint32(ret + 8, true);
                var len9 = dataView(memory0).getUint32(ret + 12, true);
                var result9 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr9, len9));
                variant13= {
                  tag: 'not-a-vhlink',
                  val: result9
                };
                break;
              }
              case 2: {
                var ptr10 = dataView(memory0).getUint32(ret + 8, true);
                var len10 = dataView(memory0).getUint32(ret + 12, true);
                var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                variant13= {
                  tag: 'manifest',
                  val: result10
                };
                break;
              }
              case 3: {
                var ptr11 = dataView(memory0).getUint32(ret + 8, true);
                var len11 = dataView(memory0).getUint32(ret + 12, true);
                var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                variant13= {
                  tag: 'decrypt-failed',
                  val: result11
                };
                break;
              }
              case 4: {
                var ptr12 = dataView(memory0).getUint32(ret + 8, true);
                var len12 = dataView(memory0).getUint32(ret + 12, true);
                var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                variant13= {
                  tag: 'unsupported',
                  val: result12
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for VhlError');
              }
            }
            variant14= {
              tag: 'err',
              val: variant13
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="encrypt"][Instruction::Return]', {
          funcName: 'encrypt',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant14;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn13(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010Decrypt;

function decrypt(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010Decrypt',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr1= encodeRes.ptr;
        var len1 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="decrypt"][Instruction::CallWasm] enter', {
          funcName: 'decrypt',
          paramCount: 4,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010Decrypt(ptr0, len0, ptr1, len1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant15;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            let variant3;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                variant3 = undefined;
                break;
              }
              case 1: {
                var ptr2 = dataView(memory0).getUint32(ret + 8, true);
                var len2 = dataView(memory0).getUint32(ret + 12, true);
                var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
                variant3 = result2;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var ptr4 = dataView(memory0).getUint32(ret + 16, true);
            var len4 = dataView(memory0).getUint32(ret + 20, true);
            if (ptr4 % 1 !== 0) throw new TypeError(`list pointer [${ptr4}] is not aligned to 1`);
            var result4 = new Uint8Array(memory0.buffer.slice(ptr4, ptr4 + len4 * 1));
            variant15= {
              tag: 'ok',
              val: {
                contentType: variant3,
                content: result4,
              }
            };
            break;
          }
          case 1: {
            let variant14;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var len9 = dataView(memory0).getUint32(ret + 12, true);
                var base9 = dataView(memory0).getUint32(ret + 8, true);
                if (base9 % 4 !== 0) throw new TypeError(`list pointer [${base9}] is not aligned to 4`);
                var result9 = [];
                for (let i = 0; i < len9; i++) {
                  const base = base9 + i * 28;
                  var ptr5 = dataView(memory0).getUint32(base + 0, true);
                  var len5 = dataView(memory0).getUint32(base + 4, true);
                  var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                  var ptr6 = dataView(memory0).getUint32(base + 8, true);
                  var len6 = dataView(memory0).getUint32(base + 12, true);
                  var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                  var ptr7 = dataView(memory0).getUint32(base + 16, true);
                  var len7 = dataView(memory0).getUint32(base + 20, true);
                  var result7 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr7, len7));
                  let enum8;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum8 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum8 = 'warning';
                      break;
                    }
                    case 2: {
                      enum8 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result9.push({
                    path: result5,
                    code: result6,
                    message: result7,
                    severity: enum8,
                  });
                }
                variant14= {
                  tag: 'invalid',
                  val: result9
                };
                break;
              }
              case 1: {
                var ptr10 = dataView(memory0).getUint32(ret + 8, true);
                var len10 = dataView(memory0).getUint32(ret + 12, true);
                var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                variant14= {
                  tag: 'not-a-vhlink',
                  val: result10
                };
                break;
              }
              case 2: {
                var ptr11 = dataView(memory0).getUint32(ret + 8, true);
                var len11 = dataView(memory0).getUint32(ret + 12, true);
                var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                variant14= {
                  tag: 'manifest',
                  val: result11
                };
                break;
              }
              case 3: {
                var ptr12 = dataView(memory0).getUint32(ret + 8, true);
                var len12 = dataView(memory0).getUint32(ret + 12, true);
                var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                variant14= {
                  tag: 'decrypt-failed',
                  val: result12
                };
                break;
              }
              case 4: {
                var ptr13 = dataView(memory0).getUint32(ret + 8, true);
                var len13 = dataView(memory0).getUint32(ret + 12, true);
                var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
                variant14= {
                  tag: 'unsupported',
                  val: result13
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for VhlError');
              }
            }
            variant15= {
              tag: 'err',
              val: variant14
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="decrypt"][Instruction::Return]', {
          funcName: 'decrypt',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant15;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn14(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010BuildSignatureBase;

function buildSignatureBase(arg0) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010BuildSignatureBase',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var ptr0 = realloc0(0, 0, 8, 72);
        var {method: v1_0, authority: v1_1, path: v1_2, contentType: v1_3, body: v1_4, keyid: v1_5, alg: v1_6, created: v1_7 } = arg0;
        
        var encodeRes = _utf8AllocateAndEncode(v1_0, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 4, len2, true);
        dataView(memory0).setUint32(ptr0 + 0, ptr2, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_1, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 12, len3, true);
        dataView(memory0).setUint32(ptr0 + 8, ptr3, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_2, realloc0, memory0);
        var ptr4= encodeRes.ptr;
        var len4 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 20, len4, true);
        dataView(memory0).setUint32(ptr0 + 16, ptr4, true);
        var variant6 = v1_3;
        if (variant6 === null || variant6=== undefined) {
          dataView(memory0).setInt8(ptr0 + 24, 0, true);
        } else {
          const e = variant6;
          dataView(memory0).setInt8(ptr0 + 24, 1, true);
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr5= encodeRes.ptr;
          var len5 = encodeRes.len;
          
          dataView(memory0).setUint32(ptr0 + 32, len5, true);
          dataView(memory0).setUint32(ptr0 + 28, ptr5, true);
        }
        var variant8 = v1_4;
        if (variant8 === null || variant8=== undefined) {
          dataView(memory0).setInt8(ptr0 + 36, 0, true);
        } else {
          const e = variant8;
          dataView(memory0).setInt8(ptr0 + 36, 1, true);
          var val7 = e;
          var len7 = Array.isArray(val7) ? val7.length : val7.byteLength;
          var ptr7 = realloc0(0, 0, 1, len7 * 1);
          
          let valData7;
          const valLenBytes7 = len7 * 1;
          if (Array.isArray(val7)) {
            // Regular array likely containing numbers, write values to memory
            let offset = 0;
            const dv7 = new DataView(memory0.buffer);
            for (const v of val7) {
              _requireValidNumericPrimitive.bind(null, 'u8')(v);
              dv7.setUint8(ptr7+ offset, v, true);
              offset += 1;
            }
          } else {
            // TypedArray / ArrayBuffer-like, direct copy
            valData7 = new Uint8Array(val7.buffer || val7, val7.byteOffset, valLenBytes7);
            const out7 = new Uint8Array(memory0.buffer, ptr7, valLenBytes7);
            out7.set(valData7);
          }
          
          dataView(memory0).setUint32(ptr0 + 44, len7, true);
          dataView(memory0).setUint32(ptr0 + 40, ptr7, true);
        }
        
        var encodeRes = _utf8AllocateAndEncode(v1_5, realloc0, memory0);
        var ptr9= encodeRes.ptr;
        var len9 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 52, len9, true);
        dataView(memory0).setUint32(ptr0 + 48, ptr9, true);
        
        var encodeRes = _utf8AllocateAndEncode(v1_6, realloc0, memory0);
        var ptr10= encodeRes.ptr;
        var len10 = encodeRes.len;
        
        dataView(memory0).setUint32(ptr0 + 60, len10, true);
        dataView(memory0).setUint32(ptr0 + 56, ptr10, true);
        dataView(memory0).setBigInt64(ptr0 + 64, toInt64(v1_7), true);
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="build-signature-base"][Instruction::CallWasm] enter', {
          funcName: 'build-signature-base',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010BuildSignatureBase(ptr0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant12;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant12 = undefined;
            break;
          }
          case 1: {
            var ptr11 = dataView(memory0).getUint32(ret + 4, true);
            var len11 = dataView(memory0).getUint32(ret + 8, true);
            var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
            variant12 = result11;
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for option');
          }
        }
        var ptr13 = dataView(memory0).getUint32(ret + 12, true);
        var len13 = dataView(memory0).getUint32(ret + 16, true);
        var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
        var ptr14 = dataView(memory0).getUint32(ret + 20, true);
        var len14 = dataView(memory0).getUint32(ret + 24, true);
        if (ptr14 % 1 !== 0) throw new TypeError(`list pointer [${ptr14}] is not aligned to 1`);
        var result14 = new Uint8Array(memory0.buffer.slice(ptr14, ptr14 + len14 * 1));
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="build-signature-base"][Instruction::Return]', {
          funcName: 'build-signature-base',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([{
          contentDigest: variant12,
          signatureInput: result13,
          base: result14,
        }]);
        const retCopy = {
          contentDigest: variant12,
          signatureInput: result13,
          base: result14,
        };
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn15(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let vhl010SignatureHeader;

function signatureHeader(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'vhl010SignatureHeader',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr1= encodeRes.ptr;
        var len1 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="signature-header"][Instruction::CallWasm] enter', {
          funcName: 'signature-header',
          paramCount: 4,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => vhl010SignatureHeader(ptr0, len0, ptr1, len1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant13;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr2 = dataView(memory0).getUint32(ret + 4, true);
            var len2 = dataView(memory0).getUint32(ret + 8, true);
            var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
            variant13= {
              tag: 'ok',
              val: result2
            };
            break;
          }
          case 1: {
            let variant12;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var len7 = dataView(memory0).getUint32(ret + 12, true);
                var base7 = dataView(memory0).getUint32(ret + 8, true);
                if (base7 % 4 !== 0) throw new TypeError(`list pointer [${base7}] is not aligned to 4`);
                var result7 = [];
                for (let i = 0; i < len7; i++) {
                  const base = base7 + i * 28;
                  var ptr3 = dataView(memory0).getUint32(base + 0, true);
                  var len3 = dataView(memory0).getUint32(base + 4, true);
                  var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                  var ptr4 = dataView(memory0).getUint32(base + 8, true);
                  var len4 = dataView(memory0).getUint32(base + 12, true);
                  var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                  var ptr5 = dataView(memory0).getUint32(base + 16, true);
                  var len5 = dataView(memory0).getUint32(base + 20, true);
                  var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                  let enum6;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum6 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum6 = 'warning';
                      break;
                    }
                    case 2: {
                      enum6 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result7.push({
                    path: result3,
                    code: result4,
                    message: result5,
                    severity: enum6,
                  });
                }
                variant12= {
                  tag: 'invalid',
                  val: result7
                };
                break;
              }
              case 1: {
                var ptr8 = dataView(memory0).getUint32(ret + 8, true);
                var len8 = dataView(memory0).getUint32(ret + 12, true);
                var result8 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr8, len8));
                variant12= {
                  tag: 'not-a-vhlink',
                  val: result8
                };
                break;
              }
              case 2: {
                var ptr9 = dataView(memory0).getUint32(ret + 8, true);
                var len9 = dataView(memory0).getUint32(ret + 12, true);
                var result9 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr9, len9));
                variant12= {
                  tag: 'manifest',
                  val: result9
                };
                break;
              }
              case 3: {
                var ptr10 = dataView(memory0).getUint32(ret + 8, true);
                var len10 = dataView(memory0).getUint32(ret + 12, true);
                var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                variant12= {
                  tag: 'decrypt-failed',
                  val: result10
                };
                break;
              }
              case 4: {
                var ptr11 = dataView(memory0).getUint32(ret + 8, true);
                var len11 = dataView(memory0).getUint32(ret + 12, true);
                var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                variant12= {
                  tag: 'unsupported',
                  val: result11
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for VhlError');
              }
            }
            variant13= {
              tag: 'err',
              val: variant12
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/vhl@0.1.0", function="signature-header"][Instruction::Return]', {
          funcName: 'signature-header',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant13;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn13(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}

const handleTable1 = [T_FLAG, 0];
handleTable1._createdReps = new Set();
handleTable1._componentIdx = 0;
const finalizationRegistry1 = finalizationRegistryCreate((handle) => {
  const { rep } = rscTableRemove(handleTable1, handle);
  exports0['3'](rep);
});

HANDLE_TABLES[1] = handleTable1;
let trust010ConstructorTrustStore;

class TrustStore{
  constructor() {
    
    const hostProvided = false;
    getOrCreateAsyncState(0).throwIfTrapped();
    
    const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
      componentIdx: 0,
      isAsync: false,
      isManualAsync: false,
      preserveFutureResult: false,
      entryFnName: 'trust010ConstructorTrustStore',
      getCallbackFn: () => null,
      callbackFnName: null,
      errHandling: 'none',
      callingWasmExport: true,
    });
    task.setCalleeIsAsync(false);
    
    const started = task.enterSync();
    CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
    
    if (null!== null) {
      task.setReturnMemoryIdx(null);
      task.setReturnMemory((() => null)());
    }
    
    
    return _withGlobalCurrentTaskMeta({
      taskID: task.id(),
      componentIdx: task.componentIdx(),
      fn: () => {
        try {
          
          _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[constructor]trust-store"][Instruction::CallWasm] enter', {
            funcName: '[constructor]trust-store',
            paramCount: 0,
            async: false,
            postReturn: false,
          });
          
          let ret;
          
          try {
            ret =  _withGlobalCurrentTaskMeta({
              taskID: task.id(),
              componentIdx: task.componentIdx(),
              fn: () => trust010ConstructorTrustStore(),
            });
          } catch (err) {
            
            _debugLog('[Instruction::CallWasm] error during sync call', {
              taskID: task.id(),
              err,
            });
            getOrCreateAsyncState(0).markTrapped(err);
            task.setErrored(err);
            task.reject(err);
            task.exit();
            throw err;
            
          }
          
          var handle1 = ret;
          var rsc0 = new.target === TrustStore ? this : Object.create(TrustStore.prototype);
          Object.defineProperty(rsc0, symbolRscHandle, { writable: true, value: handle1});
          finalizationRegistry1.register(rsc0, handle1, rsc0);
          Object.defineProperty(rsc0, symbolDispose, { writable: true, value: function () {
            finalizationRegistry1.unregister(rsc0);
            const handleEntry = rscTableRemove(handleTable1, handle1);
            rsc0[symbolDispose] = emptyFunc;
            rsc0[symbolRscHandle] = undefined;
            exports0['3'](handleEntry.rep);
          }});
          _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[constructor]trust-store"][Instruction::Return]', {
            funcName: '[constructor]trust-store',
            paramCount: 1,
            async: false,
            postReturn: false
          });
          task.resolve([rsc0]);
          task.exit();
          return rsc0;
          
        } catch (err) {
          if (!task.isResolvedState()) {
            task.setErrored(err);
            task.reject(err);
          }
          if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
          throw err;
        }
      },
    });
    
  }
}
let trust010MethodTrustStoreAddPem;

TrustStore.prototype.addPem = function addPem(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreAddPem',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-pem"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.add-pem',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreAddPem(handle0, ptr2, len2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant7;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant7= {
              tag: 'ok',
              val: dataView(memory0).getInt32(ret + 4, true) >>> 0
            };
            break;
          }
          case 1: {
            let variant6;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant6= {
                  tag: 'parse',
                  val: result3
                };
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant6= {
                  tag: 'unknown-trust-domain',
                  val: result4
                };
                break;
              }
              case 2: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant6= {
                  tag: 'proof-invalid',
                  val: result5
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for TrustError');
              }
            }
            variant7= {
              tag: 'err',
              val: variant6
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-pem"][Instruction::Return]', {
          funcName: '[method]trust-store.add-pem',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant7;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn16(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreAddDer;

TrustStore.prototype.addDer = function addDer(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreAddDer',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        var val2 = arg1;
        var len2 = Array.isArray(val2) ? val2.length : val2.byteLength;
        var ptr2 = realloc0(0, 0, 1, len2 * 1);
        
        let valData2;
        const valLenBytes2 = len2 * 1;
        if (Array.isArray(val2)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv2 = new DataView(memory0.buffer);
          for (const v of val2) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv2.setUint8(ptr2+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData2 = new Uint8Array(val2.buffer || val2, val2.byteOffset, valLenBytes2);
          const out2 = new Uint8Array(memory0.buffer, ptr2, valLenBytes2);
          out2.set(valData2);
        }
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-der"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.add-der',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreAddDer(handle0, ptr2, len2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant7;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant7= {
              tag: 'ok',
              val: dataView(memory0).getInt32(ret + 4, true) >>> 0
            };
            break;
          }
          case 1: {
            let variant6;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant6= {
                  tag: 'parse',
                  val: result3
                };
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant6= {
                  tag: 'unknown-trust-domain',
                  val: result4
                };
                break;
              }
              case 2: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant6= {
                  tag: 'proof-invalid',
                  val: result5
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for TrustError');
              }
            }
            variant7= {
              tag: 'err',
              val: variant6
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-der"][Instruction::Return]', {
          funcName: '[method]trust-store.add-der',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant7;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn16(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreAddDidDocument;

TrustStore.prototype.addDidDocument = function addDidDocument(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreAddDidDocument',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-did-document"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.add-did-document',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreAddDidDocument(handle0, ptr2, len2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant7;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant7= {
              tag: 'ok',
              val: dataView(memory0).getInt32(ret + 4, true) >>> 0
            };
            break;
          }
          case 1: {
            let variant6;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant6= {
                  tag: 'parse',
                  val: result3
                };
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant6= {
                  tag: 'unknown-trust-domain',
                  val: result4
                };
                break;
              }
              case 2: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant6= {
                  tag: 'proof-invalid',
                  val: result5
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for TrustError');
              }
            }
            variant7= {
              tag: 'err',
              val: variant6
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-did-document"][Instruction::Return]', {
          funcName: '[method]trust-store.add-did-document',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant7;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn16(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreAddVerifiedDidDocument;

TrustStore.prototype.addVerifiedDidDocument = function addVerifiedDidDocument(arg1, arg2) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreAddVerifiedDidDocument',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg2, realloc0, memory0);
        var ptr3= encodeRes.ptr;
        var len3 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-verified-did-document"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.add-verified-did-document',
          paramCount: 5,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreAddVerifiedDidDocument(handle0, ptr2, len2, ptr3, len3),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant8;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant8= {
              tag: 'ok',
              val: dataView(memory0).getInt32(ret + 4, true) >>> 0
            };
            break;
          }
          case 1: {
            let variant7;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant7= {
                  tag: 'parse',
                  val: result4
                };
                break;
              }
              case 1: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant7= {
                  tag: 'unknown-trust-domain',
                  val: result5
                };
                break;
              }
              case 2: {
                var ptr6 = dataView(memory0).getUint32(ret + 8, true);
                var len6 = dataView(memory0).getUint32(ret + 12, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant7= {
                  tag: 'proof-invalid',
                  val: result6
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for TrustError');
              }
            }
            variant8= {
              tag: 'err',
              val: variant7
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.add-verified-did-document"][Instruction::Return]', {
          funcName: '[method]trust-store.add-verified-did-document',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant8;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn16(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreLoadTrustDomains;

TrustStore.prototype.loadTrustDomains = function loadTrustDomains(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreLoadTrustDomains',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr2= encodeRes.ptr;
        var len2 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.load-trust-domains"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.load-trust-domains',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreLoadTrustDomains(handle0, ptr2, len2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant7;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant7= {
              tag: 'ok',
              val: dataView(memory0).getInt32(ret + 4, true) >>> 0
            };
            break;
          }
          case 1: {
            let variant6;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant6= {
                  tag: 'parse',
                  val: result3
                };
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant6= {
                  tag: 'unknown-trust-domain',
                  val: result4
                };
                break;
              }
              case 2: {
                var ptr5 = dataView(memory0).getUint32(ret + 8, true);
                var len5 = dataView(memory0).getUint32(ret + 12, true);
                var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                variant6= {
                  tag: 'proof-invalid',
                  val: result5
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for TrustError');
              }
            }
            variant7= {
              tag: 'err',
              val: variant6
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.load-trust-domains"][Instruction::Return]', {
          funcName: '[method]trust-store.load-trust-domains',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant7;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn16(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreTrustDomains;

TrustStore.prototype.trustDomains = function trustDomains() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreTrustDomains',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.trust-domains"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.trust-domains',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreTrustDomains(handle0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var len3 = dataView(memory0).getUint32(ret + 4, true);
        var base3 = dataView(memory0).getUint32(ret + 0, true);
        if (base3 % 4 !== 0) throw new TypeError(`list pointer [${base3}] is not aligned to 4`);
        var result3 = [];
        for (let i = 0; i < len3; i++) {
          const base = base3 + i * 8;
          var ptr2 = dataView(memory0).getUint32(base + 0, true);
          var len2 = dataView(memory0).getUint32(base + 4, true);
          var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
          result3.push(result2);
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.trust-domains"][Instruction::Return]', {
          funcName: '[method]trust-store.trust-domains',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result3]);
        const retCopy = result3;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn17(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreCandidates;

TrustStore.prototype.candidates = function candidates(arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreCandidates',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        var val2 = arg1;
        var len2 = Array.isArray(val2) ? val2.length : val2.byteLength;
        var ptr2 = realloc0(0, 0, 1, len2 * 1);
        
        let valData2;
        const valLenBytes2 = len2 * 1;
        if (Array.isArray(val2)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv2 = new DataView(memory0.buffer);
          for (const v of val2) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv2.setUint8(ptr2+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData2 = new Uint8Array(val2.buffer || val2, val2.byteOffset, valLenBytes2);
          const out2 = new Uint8Array(memory0.buffer, ptr2, valLenBytes2);
          out2.set(valData2);
        }
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.candidates"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.candidates',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreCandidates(handle0, ptr2, len2),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var len13 = dataView(memory0).getUint32(ret + 4, true);
        var base13 = dataView(memory0).getUint32(ret + 0, true);
        if (base13 % 8 !== 0) throw new TypeError(`list pointer [${base13}] is not aligned to 8`);
        var result13 = [];
        for (let i = 0; i < len13; i++) {
          const base = base13 + i * 80;
          var ptr3 = dataView(memory0).getUint32(base + 0, true);
          var len3 = dataView(memory0).getUint32(base + 4, true);
          if (ptr3 % 1 !== 0) throw new TypeError(`list pointer [${ptr3}] is not aligned to 1`);
          var result3 = new Uint8Array(memory0.buffer.slice(ptr3, ptr3 + len3 * 1));
          var ptr4 = dataView(memory0).getUint32(base + 8, true);
          var len4 = dataView(memory0).getUint32(base + 12, true);
          if (ptr4 % 1 !== 0) throw new TypeError(`list pointer [${ptr4}] is not aligned to 1`);
          var result4 = new Uint8Array(memory0.buffer.slice(ptr4, ptr4 + len4 * 1));
          let variant6;
          switch (dataView(memory0).getUint8(base + 16, true)) {
            case 0: {
              variant6 = undefined;
              break;
            }
            case 1: {
              var ptr5 = dataView(memory0).getUint32(base + 20, true);
              var len5 = dataView(memory0).getUint32(base + 24, true);
              var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
              variant6 = result5;
              break;
            }
            default: {
              throw new TypeError('invalid variant discriminant for option');
            }
          }
          var len8 = dataView(memory0).getUint32(base + 52, true);
          var base8 = dataView(memory0).getUint32(base + 48, true);
          if (base8 % 4 !== 0) throw new TypeError(`list pointer [${base8}] is not aligned to 4`);
          var result8 = [];
          for (let i = 0; i < len8; i++) {
            const base = base8 + i * 8;
            var ptr7 = dataView(memory0).getUint32(base + 0, true);
            var len7 = dataView(memory0).getUint32(base + 4, true);
            var result7 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr7, len7));
            result8.push(result7);
          }
          let variant10;
          switch (dataView(memory0).getUint8(base + 56, true)) {
            case 0: {
              variant10 = undefined;
              break;
            }
            case 1: {
              var ptr9 = dataView(memory0).getUint32(base + 60, true);
              var len9 = dataView(memory0).getUint32(base + 64, true);
              var result9 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr9, len9));
              variant10 = result9;
              break;
            }
            default: {
              throw new TypeError('invalid variant discriminant for option');
            }
          }
          let variant12;
          switch (dataView(memory0).getUint8(base + 68, true)) {
            case 0: {
              variant12 = undefined;
              break;
            }
            case 1: {
              var ptr11 = dataView(memory0).getUint32(base + 72, true);
              var len11 = dataView(memory0).getUint32(base + 76, true);
              var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
              variant12 = result11;
              break;
            }
            default: {
              throw new TypeError('invalid variant discriminant for option');
            }
          }
          result13.push({
            der: result3,
            kid: result4,
            country: variant6,
            notBefore: dataView(memory0).getBigInt64(base + 32, true),
            notAfter: dataView(memory0).getBigInt64(base + 40, true),
            extendedKeyUsages: result8,
            domain: variant10,
            participant: variant12,
          });
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.candidates"][Instruction::Return]', {
          funcName: '[method]trust-store.candidates',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result13]);
        const retCopy = result13;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn18(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreSize;

TrustStore.prototype.size = function size() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreSize',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (null!== null) {
    task.setReturnMemoryIdx(null);
    task.setReturnMemory((() => null)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.size"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.size',
          paramCount: 1,
          async: false,
          postReturn: false,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreSize(handle0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.size"][Instruction::Return]', {
          funcName: '[method]trust-store.size',
          paramCount: 1,
          async: false,
          postReturn: false
        });
        task.resolve([ret >>> 0]);
        task.exit();
        return ret >>> 0;
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010MethodTrustStoreClear;

TrustStore.prototype.clear = function clear() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010MethodTrustStoreClear',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (null!== null) {
    task.setReturnMemoryIdx(null);
    task.setReturnMemory((() => null)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable1[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle0 = handleTable1[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.clear"][Instruction::CallWasm] enter', {
          funcName: '[method]trust-store.clear',
          paramCount: 1,
          async: false,
          postReturn: false,
        });
        
        let ret;
        
        try {
          _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010MethodTrustStoreClear(handle0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="[method]trust-store.clear"][Instruction::Return]', {
          funcName: '[method]trust-store.clear',
          paramCount: 0,
          async: false,
          postReturn: false
        });
        task.resolve([ret]);
        task.exit();
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let trust010VerifyDidProof;

function verifyDidProof(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'trust010VerifyDidProof',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        
        var encodeRes = _utf8AllocateAndEncode(arg1, realloc0, memory0);
        var ptr1= encodeRes.ptr;
        var len1 = encodeRes.len;
        
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="verify-did-proof"][Instruction::CallWasm] enter', {
          funcName: 'verify-did-proof',
          paramCount: 4,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => trust010VerifyDidProof(ptr0, len0, ptr1, len1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant6;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            variant6= {
              tag: 'ok',
              val: undefined
            };
            break;
          }
          case 1: {
            let variant5;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var ptr2 = dataView(memory0).getUint32(ret + 8, true);
                var len2 = dataView(memory0).getUint32(ret + 12, true);
                var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
                variant5= {
                  tag: 'parse',
                  val: result2
                };
                break;
              }
              case 1: {
                var ptr3 = dataView(memory0).getUint32(ret + 8, true);
                var len3 = dataView(memory0).getUint32(ret + 12, true);
                var result3 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr3, len3));
                variant5= {
                  tag: 'unknown-trust-domain',
                  val: result3
                };
                break;
              }
              case 2: {
                var ptr4 = dataView(memory0).getUint32(ret + 8, true);
                var len4 = dataView(memory0).getUint32(ret + 12, true);
                var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                variant5= {
                  tag: 'proof-invalid',
                  val: result4
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for TrustError');
              }
            }
            variant6= {
              tag: 'err',
              val: variant5
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/trust@0.1.0", function="verify-did-proof"][Instruction::Return]', {
          funcName: 'verify-did-proof',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant6;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn16(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let verifier010Verify;

function verify(arg0, arg1, arg2, arg3, arg4) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'verifier010Verify',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        
        var handle2 = arg1[symbolRscHandle];
        if (!handle2 || (handleTable1[(handle2 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle1 = handleTable1[(handle2 << 1) + 1] & ~T_FLAG;
        
        
        var handle4 = arg2[symbolRscHandle];
        if (!handle4 || (handleTable2[(handle4 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle3 = handleTable2[(handle4 << 1) + 1] & ~T_FLAG;
        
        var {limits: v5_0, clockSkewSeconds: v5_1, maxPayloads: v5_2 } = arg4;
        var {maxInputChars: v6_0, maxInflatedBytes: v6_1, maxDepth: v6_2, maxItems: v6_3, maxStringBytes: v6_4 } = v5_0;
        var variant7 = v5_2;
        let variant7_0;
        let variant7_1;
        if (variant7 === null || variant7=== undefined) {
          variant7_0 = 0;
          variant7_1 = 0;
        } else {
          const e = variant7;
          variant7_0 = 1;
          variant7_1 = toUint32(e);
        }
        _debugLog('[iface="who:smart-trust/verifier@0.1.0", function="verify"][Instruction::CallWasm] enter', {
          funcName: 'verify',
          paramCount: 13,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => verifier010Verify(ptr0, len0, handle1, handle3, toInt64(arg3), toUint32(v6_0), toUint32(v6_1), toUint32(v6_2), toUint32(v6_3), toUint32(v6_4), toUint32(v5_1), variant7_0, variant7_1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant69;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            let variant9;
            switch (dataView(memory0).getUint8(ret + 8, true)) {
              case 0: {
                variant9 = undefined;
                break;
              }
              case 1: {
                var ptr8 = dataView(memory0).getUint32(ret + 12, true);
                var len8 = dataView(memory0).getUint32(ret + 16, true);
                var result8 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr8, len8));
                variant9 = result8;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var len44 = dataView(memory0).getUint32(ret + 44, true);
            var base44 = dataView(memory0).getUint32(ret + 40, true);
            if (base44 % 8 !== 0) throw new TypeError(`list pointer [${base44}] is not aligned to 8`);
            var result44 = [];
            for (let i = 0; i < len44; i++) {
              const base = base44 + i * 160;
              let variant42;
              switch (dataView(memory0).getUint8(base + 8, true)) {
                case 0: {
                  var ptr10 = dataView(memory0).getUint32(base + 16, true);
                  var len10 = dataView(memory0).getUint32(base + 20, true);
                  var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                  var ptr11 = dataView(memory0).getUint32(base + 24, true);
                  var len11 = dataView(memory0).getUint32(base + 28, true);
                  var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                  var ptr12 = dataView(memory0).getUint32(base + 32, true);
                  var len12 = dataView(memory0).getUint32(base + 36, true);
                  var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                  var ptr13 = dataView(memory0).getUint32(base + 40, true);
                  var len13 = dataView(memory0).getUint32(base + 44, true);
                  var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
                  let variant15;
                  switch (dataView(memory0).getUint8(base + 48, true)) {
                    case 0: {
                      variant15 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr14 = dataView(memory0).getUint32(base + 52, true);
                      var len14 = dataView(memory0).getUint32(base + 56, true);
                      var result14 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr14, len14));
                      variant15 = result14;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant17;
                  switch (dataView(memory0).getUint8(base + 60, true)) {
                    case 0: {
                      variant17 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr16 = dataView(memory0).getUint32(base + 64, true);
                      var len16 = dataView(memory0).getUint32(base + 68, true);
                      var result16 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr16, len16));
                      variant17 = result16;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant19;
                  switch (dataView(memory0).getUint8(base + 72, true)) {
                    case 0: {
                      variant19 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr18 = dataView(memory0).getUint32(base + 76, true);
                      var len18 = dataView(memory0).getUint32(base + 80, true);
                      var result18 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr18, len18));
                      variant19 = result18;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  var ptr20 = dataView(memory0).getUint32(base + 84, true);
                  var len20 = dataView(memory0).getUint32(base + 88, true);
                  var result20 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr20, len20));
                  var ptr21 = dataView(memory0).getUint32(base + 92, true);
                  var len21 = dataView(memory0).getUint32(base + 96, true);
                  var result21 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr21, len21));
                  let variant23;
                  switch (dataView(memory0).getUint8(base + 100, true)) {
                    case 0: {
                      variant23 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr22 = dataView(memory0).getUint32(base + 104, true);
                      var len22 = dataView(memory0).getUint32(base + 108, true);
                      var result22 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr22, len22));
                      variant23 = result22;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant25;
                  switch (dataView(memory0).getUint8(base + 112, true)) {
                    case 0: {
                      variant25 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr24 = dataView(memory0).getUint32(base + 116, true);
                      var len24 = dataView(memory0).getUint32(base + 120, true);
                      var result24 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr24, len24));
                      variant25 = result24;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  var ptr26 = dataView(memory0).getUint32(base + 124, true);
                  var len26 = dataView(memory0).getUint32(base + 128, true);
                  var result26 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr26, len26));
                  var ptr27 = dataView(memory0).getUint32(base + 132, true);
                  var len27 = dataView(memory0).getUint32(base + 136, true);
                  var result27 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr27, len27));
                  var len30 = dataView(memory0).getUint32(base + 144, true);
                  var base30 = dataView(memory0).getUint32(base + 140, true);
                  if (base30 % 4 !== 0) throw new TypeError(`list pointer [${base30}] is not aligned to 4`);
                  var result30 = [];
                  for (let i = 0; i < len30; i++) {
                    const base = base30 + i * 16;
                    var ptr28 = dataView(memory0).getUint32(base + 0, true);
                    var len28 = dataView(memory0).getUint32(base + 4, true);
                    var result28 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr28, len28));
                    var ptr29 = dataView(memory0).getUint32(base + 8, true);
                    var len29 = dataView(memory0).getUint32(base + 12, true);
                    if (ptr29 % 1 !== 0) throw new TypeError(`list pointer [${ptr29}] is not aligned to 1`);
                    var result29 = new Uint8Array(memory0.buffer.slice(ptr29, ptr29 + len29 * 1));
                    result30.push([result28, result29]);
                  }
                  variant42= {
                    tag: 'dvc-min',
                    val: {
                      n: result10,
                      dob: result11,
                      s: result12,
                      nt: result13,
                      id: variant15,
                      dt: variant17,
                      gn: variant19,
                      vx: {
                        vp: result20,
                        dv: result21,
                        cn: variant23,
                        is: variant25,
                        bo: result26,
                      },
                      v: result27,
                      extensions: result30,
                    }
                  };
                  break;
                }
                case 1: {
                  var ptr31 = dataView(memory0).getUint32(base + 16, true);
                  var len31 = dataView(memory0).getUint32(base + 20, true);
                  var result31 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr31, len31));
                  var ptr32 = dataView(memory0).getUint32(base + 24, true);
                  var len32 = dataView(memory0).getUint32(base + 28, true);
                  var result32 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr32, len32));
                  let variant33;
                  switch (dataView(memory0).getUint8(base + 32, true)) {
                    case 0: {
                      variant33 = undefined;
                      break;
                    }
                    case 1: {
                      variant33 = dataView(memory0).getBigInt64(base + 40, true);
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant35;
                  switch (dataView(memory0).getUint8(base + 48, true)) {
                    case 0: {
                      variant35 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr34 = dataView(memory0).getUint32(base + 52, true);
                      var len34 = dataView(memory0).getUint32(base + 56, true);
                      var result34 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr34, len34));
                      variant35 = result34;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant37;
                  switch (dataView(memory0).getUint8(base + 60, true)) {
                    case 0: {
                      variant37 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr36 = dataView(memory0).getUint32(base + 64, true);
                      var len36 = dataView(memory0).getUint32(base + 68, true);
                      var result36 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr36, len36));
                      variant37 = result36;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant38;
                  switch (dataView(memory0).getUint8(base + 72, true)) {
                    case 0: {
                      variant38 = undefined;
                      break;
                    }
                    case 1: {
                      variant38 = dataView(memory0).getInt32(base + 76, true);
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant40;
                  switch (dataView(memory0).getUint8(base + 80, true)) {
                    case 0: {
                      variant40 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr39 = dataView(memory0).getUint32(base + 84, true);
                      var len39 = dataView(memory0).getUint32(base + 88, true);
                      var result39 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr39, len39));
                      variant40 = result39;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  variant42= {
                    tag: 'vhl',
                    val: {
                      url: result31,
                      key: result32,
                      exp: variant33,
                      flag: variant35,
                      label: variant37,
                      v: variant38,
                      fhirBaseUrl: variant40,
                    }
                  };
                  break;
                }
                case 2: {
                  var ptr41 = dataView(memory0).getUint32(base + 20, true);
                  var len41 = dataView(memory0).getUint32(base + 24, true);
                  if (ptr41 % 1 !== 0) throw new TypeError(`list pointer [${ptr41}] is not aligned to 1`);
                  var result41 = new Uint8Array(memory0.buffer.slice(ptr41, ptr41 + len41 * 1));
                  variant42= {
                    tag: 'raw',
                    val: {
                      subclaim: dataView(memory0).getInt32(base + 16, true),
                      cbor: result41,
                    }
                  };
                  break;
                }
                default: {
                  throw new TypeError('invalid variant discriminant for Payload');
                }
              }
              var bool43 = dataView(memory0).getUint8(base + 152, true);
              result44.push({
                subclaim: dataView(memory0).getInt32(base + 0, true),
                payload: variant42,
                keyUsageAuthorised: bool43 == 0 ? false : (bool43 == 1 ? true : throwInvalidBool()),
              });
            }
            var ptr45 = dataView(memory0).getUint32(ret + 48, true);
            var len45 = dataView(memory0).getUint32(ret + 52, true);
            if (ptr45 % 1 !== 0) throw new TypeError(`list pointer [${ptr45}] is not aligned to 1`);
            var result45 = new Uint8Array(memory0.buffer.slice(ptr45, ptr45 + len45 * 1));
            var ptr46 = dataView(memory0).getUint32(ret + 56, true);
            var len46 = dataView(memory0).getUint32(ret + 60, true);
            if (ptr46 % 1 !== 0) throw new TypeError(`list pointer [${ptr46}] is not aligned to 1`);
            var result46 = new Uint8Array(memory0.buffer.slice(ptr46, ptr46 + len46 * 1));
            let variant48;
            switch (dataView(memory0).getUint8(ret + 64, true)) {
              case 0: {
                variant48 = undefined;
                break;
              }
              case 1: {
                var ptr47 = dataView(memory0).getUint32(ret + 68, true);
                var len47 = dataView(memory0).getUint32(ret + 72, true);
                var result47 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr47, len47));
                variant48 = result47;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var len50 = dataView(memory0).getUint32(ret + 100, true);
            var base50 = dataView(memory0).getUint32(ret + 96, true);
            if (base50 % 4 !== 0) throw new TypeError(`list pointer [${base50}] is not aligned to 4`);
            var result50 = [];
            for (let i = 0; i < len50; i++) {
              const base = base50 + i * 8;
              var ptr49 = dataView(memory0).getUint32(base + 0, true);
              var len49 = dataView(memory0).getUint32(base + 4, true);
              var result49 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr49, len49));
              result50.push(result49);
            }
            let variant52;
            switch (dataView(memory0).getUint8(ret + 104, true)) {
              case 0: {
                variant52 = undefined;
                break;
              }
              case 1: {
                var ptr51 = dataView(memory0).getUint32(ret + 108, true);
                var len51 = dataView(memory0).getUint32(ret + 112, true);
                var result51 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr51, len51));
                variant52 = result51;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant54;
            switch (dataView(memory0).getUint8(ret + 116, true)) {
              case 0: {
                variant54 = undefined;
                break;
              }
              case 1: {
                var ptr53 = dataView(memory0).getUint32(ret + 120, true);
                var len53 = dataView(memory0).getUint32(ret + 124, true);
                var result53 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr53, len53));
                variant54 = result53;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var len57 = dataView(memory0).getUint32(ret + 132, true);
            var base57 = dataView(memory0).getUint32(ret + 128, true);
            if (base57 % 4 !== 0) throw new TypeError(`list pointer [${base57}] is not aligned to 4`);
            var result57 = [];
            for (let i = 0; i < len57; i++) {
              const base = base57 + i * 16;
              var ptr55 = dataView(memory0).getUint32(base + 0, true);
              var len55 = dataView(memory0).getUint32(base + 4, true);
              if (ptr55 % 1 !== 0) throw new TypeError(`list pointer [${ptr55}] is not aligned to 1`);
              var result55 = new Uint8Array(memory0.buffer.slice(ptr55, ptr55 + len55 * 1));
              var ptr56 = dataView(memory0).getUint32(base + 8, true);
              var len56 = dataView(memory0).getUint32(base + 12, true);
              if (ptr56 % 1 !== 0) throw new TypeError(`list pointer [${ptr56}] is not aligned to 1`);
              var result56 = new Uint8Array(memory0.buffer.slice(ptr56, ptr56 + len56 * 1));
              result57.push([result55, result56]);
            }
            var len61 = dataView(memory0).getUint32(ret + 140, true);
            var base61 = dataView(memory0).getUint32(ret + 136, true);
            if (base61 % 4 !== 0) throw new TypeError(`list pointer [${base61}] is not aligned to 4`);
            var result61 = [];
            for (let i = 0; i < len61; i++) {
              const base = base61 + i * 12;
              let enum58;
              switch (dataView(memory0).getUint8(base + 0, true)) {
                case 0: {
                  enum58 = 'prefix';
                  break;
                }
                case 1: {
                  enum58 = 'base45';
                  break;
                }
                case 2: {
                  enum58 = 'inflate';
                  break;
                }
                case 3: {
                  enum58 = 'cose';
                  break;
                }
                case 4: {
                  enum58 = 'header';
                  break;
                }
                case 5: {
                  enum58 = 'trust-lookup';
                  break;
                }
                case 6: {
                  enum58 = 'signature';
                  break;
                }
                case 7: {
                  enum58 = 'key-usage';
                  break;
                }
                case 8: {
                  enum58 = 'cwt';
                  break;
                }
                case 9: {
                  enum58 = 'time';
                  break;
                }
                case 10: {
                  enum58 = 'hcert';
                  break;
                }
                case 11: {
                  enum58 = 'payload';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Step');
                }
              }
              let enum59;
              switch (dataView(memory0).getUint8(base + 1, true)) {
                case 0: {
                  enum59 = 'blocking';
                  break;
                }
                case 1: {
                  enum59 = 'warning';
                  break;
                }
                case 2: {
                  enum59 = 'information';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Severity');
                }
              }
              var ptr60 = dataView(memory0).getUint32(base + 4, true);
              var len60 = dataView(memory0).getUint32(base + 8, true);
              var result60 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr60, len60));
              result61.push({
                step: enum58,
                severity: enum59,
                message: result60,
              });
            }
            variant69= {
              tag: 'verified',
              val: {
                iss: variant9,
                iat: dataView(memory0).getBigInt64(ret + 24, true),
                exp: dataView(memory0).getBigInt64(ret + 32, true),
                entries: result44,
                dsc: {
                  der: result45,
                  kid: result46,
                  country: variant48,
                  notBefore: dataView(memory0).getBigInt64(ret + 80, true),
                  notAfter: dataView(memory0).getBigInt64(ret + 88, true),
                  extendedKeyUsages: result50,
                  domain: variant52,
                  participant: variant54,
                },
                otherClaims: result57,
                diagnostics: result61,
              }
            };
            break;
          }
          case 1: {
            let enum62;
            switch (dataView(memory0).getUint8(ret + 8, true)) {
              case 0: {
                enum62 = 'prefix';
                break;
              }
              case 1: {
                enum62 = 'base45';
                break;
              }
              case 2: {
                enum62 = 'inflate';
                break;
              }
              case 3: {
                enum62 = 'cose';
                break;
              }
              case 4: {
                enum62 = 'header';
                break;
              }
              case 5: {
                enum62 = 'trust-lookup';
                break;
              }
              case 6: {
                enum62 = 'signature';
                break;
              }
              case 7: {
                enum62 = 'key-usage';
                break;
              }
              case 8: {
                enum62 = 'cwt';
                break;
              }
              case 9: {
                enum62 = 'time';
                break;
              }
              case 10: {
                enum62 = 'hcert';
                break;
              }
              case 11: {
                enum62 = 'payload';
                break;
              }
              default: {
                throw new TypeError('invalid discriminant specified for Step');
              }
            }
            let enum63;
            switch (dataView(memory0).getUint8(ret + 9, true)) {
              case 0: {
                enum63 = 'bad-prefix';
                break;
              }
              case 1: {
                enum63 = 'unsupported-context-version';
                break;
              }
              case 2: {
                enum63 = 'base45-invalid';
                break;
              }
              case 3: {
                enum63 = 'inflate-failed';
                break;
              }
              case 4: {
                enum63 = 'limit-exceeded';
                break;
              }
              case 5: {
                enum63 = 'cose-malformed';
                break;
              }
              case 6: {
                enum63 = 'header-invalid';
                break;
              }
              case 7: {
                enum63 = 'unsupported-algorithm';
                break;
              }
              case 8: {
                enum63 = 'kid-not-found';
                break;
              }
              case 9: {
                enum63 = 'signature-invalid';
                break;
              }
              case 10: {
                enum63 = 'key-usage-mismatch';
                break;
              }
              case 11: {
                enum63 = 'expired';
                break;
              }
              case 12: {
                enum63 = 'not-yet-valid';
                break;
              }
              case 13: {
                enum63 = 'hcert-missing';
                break;
              }
              case 14: {
                enum63 = 'multiple-subclaims';
                break;
              }
              case 15: {
                enum63 = 'payload-invalid';
                break;
              }
              default: {
                throw new TypeError('invalid discriminant specified for FailureReason');
              }
            }
            var ptr64 = dataView(memory0).getUint32(ret + 12, true);
            var len64 = dataView(memory0).getUint32(ret + 16, true);
            var result64 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr64, len64));
            var len68 = dataView(memory0).getUint32(ret + 24, true);
            var base68 = dataView(memory0).getUint32(ret + 20, true);
            if (base68 % 4 !== 0) throw new TypeError(`list pointer [${base68}] is not aligned to 4`);
            var result68 = [];
            for (let i = 0; i < len68; i++) {
              const base = base68 + i * 12;
              let enum65;
              switch (dataView(memory0).getUint8(base + 0, true)) {
                case 0: {
                  enum65 = 'prefix';
                  break;
                }
                case 1: {
                  enum65 = 'base45';
                  break;
                }
                case 2: {
                  enum65 = 'inflate';
                  break;
                }
                case 3: {
                  enum65 = 'cose';
                  break;
                }
                case 4: {
                  enum65 = 'header';
                  break;
                }
                case 5: {
                  enum65 = 'trust-lookup';
                  break;
                }
                case 6: {
                  enum65 = 'signature';
                  break;
                }
                case 7: {
                  enum65 = 'key-usage';
                  break;
                }
                case 8: {
                  enum65 = 'cwt';
                  break;
                }
                case 9: {
                  enum65 = 'time';
                  break;
                }
                case 10: {
                  enum65 = 'hcert';
                  break;
                }
                case 11: {
                  enum65 = 'payload';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Step');
                }
              }
              let enum66;
              switch (dataView(memory0).getUint8(base + 1, true)) {
                case 0: {
                  enum66 = 'blocking';
                  break;
                }
                case 1: {
                  enum66 = 'warning';
                  break;
                }
                case 2: {
                  enum66 = 'information';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Severity');
                }
              }
              var ptr67 = dataView(memory0).getUint32(base + 4, true);
              var len67 = dataView(memory0).getUint32(base + 8, true);
              var result67 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr67, len67));
              result68.push({
                step: enum65,
                severity: enum66,
                message: result67,
              });
            }
            variant69= {
              tag: 'failed',
              val: {
                step: enum62,
                reason: enum63,
                detail: result64,
                diagnostics: result68,
              }
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for VerificationResult');
          }
        }
        _debugLog('[iface="who:smart-trust/verifier@0.1.0", function="verify"][Instruction::Return]', {
          funcName: 'verify',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([variant69]);
        const retCopy = variant69;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn19(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let verifier010VerifyCose;

function verifyCose(arg0, arg1, arg2, arg3, arg4) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'verifier010VerifyCose',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var val0 = arg0;
        var len0 = Array.isArray(val0) ? val0.length : val0.byteLength;
        var ptr0 = realloc0(0, 0, 1, len0 * 1);
        
        let valData0;
        const valLenBytes0 = len0 * 1;
        if (Array.isArray(val0)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv0 = new DataView(memory0.buffer);
          for (const v of val0) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv0.setUint8(ptr0+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData0 = new Uint8Array(val0.buffer || val0, val0.byteOffset, valLenBytes0);
          const out0 = new Uint8Array(memory0.buffer, ptr0, valLenBytes0);
          out0.set(valData0);
        }
        
        
        var handle2 = arg1[symbolRscHandle];
        if (!handle2 || (handleTable1[(handle2 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"TrustStore\" resource.');
        }
        var handle1 = handleTable1[(handle2 << 1) + 1] & ~T_FLAG;
        
        
        var handle4 = arg2[symbolRscHandle];
        if (!handle4 || (handleTable2[(handle4 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle3 = handleTable2[(handle4 << 1) + 1] & ~T_FLAG;
        
        var {limits: v5_0, clockSkewSeconds: v5_1, maxPayloads: v5_2 } = arg4;
        var {maxInputChars: v6_0, maxInflatedBytes: v6_1, maxDepth: v6_2, maxItems: v6_3, maxStringBytes: v6_4 } = v5_0;
        var variant7 = v5_2;
        let variant7_0;
        let variant7_1;
        if (variant7 === null || variant7=== undefined) {
          variant7_0 = 0;
          variant7_1 = 0;
        } else {
          const e = variant7;
          variant7_0 = 1;
          variant7_1 = toUint32(e);
        }
        _debugLog('[iface="who:smart-trust/verifier@0.1.0", function="verify-cose"][Instruction::CallWasm] enter', {
          funcName: 'verify-cose',
          paramCount: 13,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => verifier010VerifyCose(ptr0, len0, handle1, handle3, toInt64(arg3), toUint32(v6_0), toUint32(v6_1), toUint32(v6_2), toUint32(v6_3), toUint32(v6_4), toUint32(v5_1), variant7_0, variant7_1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant69;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            let variant9;
            switch (dataView(memory0).getUint8(ret + 8, true)) {
              case 0: {
                variant9 = undefined;
                break;
              }
              case 1: {
                var ptr8 = dataView(memory0).getUint32(ret + 12, true);
                var len8 = dataView(memory0).getUint32(ret + 16, true);
                var result8 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr8, len8));
                variant9 = result8;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var len44 = dataView(memory0).getUint32(ret + 44, true);
            var base44 = dataView(memory0).getUint32(ret + 40, true);
            if (base44 % 8 !== 0) throw new TypeError(`list pointer [${base44}] is not aligned to 8`);
            var result44 = [];
            for (let i = 0; i < len44; i++) {
              const base = base44 + i * 160;
              let variant42;
              switch (dataView(memory0).getUint8(base + 8, true)) {
                case 0: {
                  var ptr10 = dataView(memory0).getUint32(base + 16, true);
                  var len10 = dataView(memory0).getUint32(base + 20, true);
                  var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                  var ptr11 = dataView(memory0).getUint32(base + 24, true);
                  var len11 = dataView(memory0).getUint32(base + 28, true);
                  var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                  var ptr12 = dataView(memory0).getUint32(base + 32, true);
                  var len12 = dataView(memory0).getUint32(base + 36, true);
                  var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                  var ptr13 = dataView(memory0).getUint32(base + 40, true);
                  var len13 = dataView(memory0).getUint32(base + 44, true);
                  var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
                  let variant15;
                  switch (dataView(memory0).getUint8(base + 48, true)) {
                    case 0: {
                      variant15 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr14 = dataView(memory0).getUint32(base + 52, true);
                      var len14 = dataView(memory0).getUint32(base + 56, true);
                      var result14 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr14, len14));
                      variant15 = result14;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant17;
                  switch (dataView(memory0).getUint8(base + 60, true)) {
                    case 0: {
                      variant17 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr16 = dataView(memory0).getUint32(base + 64, true);
                      var len16 = dataView(memory0).getUint32(base + 68, true);
                      var result16 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr16, len16));
                      variant17 = result16;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant19;
                  switch (dataView(memory0).getUint8(base + 72, true)) {
                    case 0: {
                      variant19 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr18 = dataView(memory0).getUint32(base + 76, true);
                      var len18 = dataView(memory0).getUint32(base + 80, true);
                      var result18 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr18, len18));
                      variant19 = result18;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  var ptr20 = dataView(memory0).getUint32(base + 84, true);
                  var len20 = dataView(memory0).getUint32(base + 88, true);
                  var result20 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr20, len20));
                  var ptr21 = dataView(memory0).getUint32(base + 92, true);
                  var len21 = dataView(memory0).getUint32(base + 96, true);
                  var result21 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr21, len21));
                  let variant23;
                  switch (dataView(memory0).getUint8(base + 100, true)) {
                    case 0: {
                      variant23 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr22 = dataView(memory0).getUint32(base + 104, true);
                      var len22 = dataView(memory0).getUint32(base + 108, true);
                      var result22 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr22, len22));
                      variant23 = result22;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant25;
                  switch (dataView(memory0).getUint8(base + 112, true)) {
                    case 0: {
                      variant25 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr24 = dataView(memory0).getUint32(base + 116, true);
                      var len24 = dataView(memory0).getUint32(base + 120, true);
                      var result24 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr24, len24));
                      variant25 = result24;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  var ptr26 = dataView(memory0).getUint32(base + 124, true);
                  var len26 = dataView(memory0).getUint32(base + 128, true);
                  var result26 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr26, len26));
                  var ptr27 = dataView(memory0).getUint32(base + 132, true);
                  var len27 = dataView(memory0).getUint32(base + 136, true);
                  var result27 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr27, len27));
                  var len30 = dataView(memory0).getUint32(base + 144, true);
                  var base30 = dataView(memory0).getUint32(base + 140, true);
                  if (base30 % 4 !== 0) throw new TypeError(`list pointer [${base30}] is not aligned to 4`);
                  var result30 = [];
                  for (let i = 0; i < len30; i++) {
                    const base = base30 + i * 16;
                    var ptr28 = dataView(memory0).getUint32(base + 0, true);
                    var len28 = dataView(memory0).getUint32(base + 4, true);
                    var result28 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr28, len28));
                    var ptr29 = dataView(memory0).getUint32(base + 8, true);
                    var len29 = dataView(memory0).getUint32(base + 12, true);
                    if (ptr29 % 1 !== 0) throw new TypeError(`list pointer [${ptr29}] is not aligned to 1`);
                    var result29 = new Uint8Array(memory0.buffer.slice(ptr29, ptr29 + len29 * 1));
                    result30.push([result28, result29]);
                  }
                  variant42= {
                    tag: 'dvc-min',
                    val: {
                      n: result10,
                      dob: result11,
                      s: result12,
                      nt: result13,
                      id: variant15,
                      dt: variant17,
                      gn: variant19,
                      vx: {
                        vp: result20,
                        dv: result21,
                        cn: variant23,
                        is: variant25,
                        bo: result26,
                      },
                      v: result27,
                      extensions: result30,
                    }
                  };
                  break;
                }
                case 1: {
                  var ptr31 = dataView(memory0).getUint32(base + 16, true);
                  var len31 = dataView(memory0).getUint32(base + 20, true);
                  var result31 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr31, len31));
                  var ptr32 = dataView(memory0).getUint32(base + 24, true);
                  var len32 = dataView(memory0).getUint32(base + 28, true);
                  var result32 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr32, len32));
                  let variant33;
                  switch (dataView(memory0).getUint8(base + 32, true)) {
                    case 0: {
                      variant33 = undefined;
                      break;
                    }
                    case 1: {
                      variant33 = dataView(memory0).getBigInt64(base + 40, true);
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant35;
                  switch (dataView(memory0).getUint8(base + 48, true)) {
                    case 0: {
                      variant35 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr34 = dataView(memory0).getUint32(base + 52, true);
                      var len34 = dataView(memory0).getUint32(base + 56, true);
                      var result34 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr34, len34));
                      variant35 = result34;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant37;
                  switch (dataView(memory0).getUint8(base + 60, true)) {
                    case 0: {
                      variant37 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr36 = dataView(memory0).getUint32(base + 64, true);
                      var len36 = dataView(memory0).getUint32(base + 68, true);
                      var result36 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr36, len36));
                      variant37 = result36;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant38;
                  switch (dataView(memory0).getUint8(base + 72, true)) {
                    case 0: {
                      variant38 = undefined;
                      break;
                    }
                    case 1: {
                      variant38 = dataView(memory0).getInt32(base + 76, true);
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  let variant40;
                  switch (dataView(memory0).getUint8(base + 80, true)) {
                    case 0: {
                      variant40 = undefined;
                      break;
                    }
                    case 1: {
                      var ptr39 = dataView(memory0).getUint32(base + 84, true);
                      var len39 = dataView(memory0).getUint32(base + 88, true);
                      var result39 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr39, len39));
                      variant40 = result39;
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for option');
                    }
                  }
                  variant42= {
                    tag: 'vhl',
                    val: {
                      url: result31,
                      key: result32,
                      exp: variant33,
                      flag: variant35,
                      label: variant37,
                      v: variant38,
                      fhirBaseUrl: variant40,
                    }
                  };
                  break;
                }
                case 2: {
                  var ptr41 = dataView(memory0).getUint32(base + 20, true);
                  var len41 = dataView(memory0).getUint32(base + 24, true);
                  if (ptr41 % 1 !== 0) throw new TypeError(`list pointer [${ptr41}] is not aligned to 1`);
                  var result41 = new Uint8Array(memory0.buffer.slice(ptr41, ptr41 + len41 * 1));
                  variant42= {
                    tag: 'raw',
                    val: {
                      subclaim: dataView(memory0).getInt32(base + 16, true),
                      cbor: result41,
                    }
                  };
                  break;
                }
                default: {
                  throw new TypeError('invalid variant discriminant for Payload');
                }
              }
              var bool43 = dataView(memory0).getUint8(base + 152, true);
              result44.push({
                subclaim: dataView(memory0).getInt32(base + 0, true),
                payload: variant42,
                keyUsageAuthorised: bool43 == 0 ? false : (bool43 == 1 ? true : throwInvalidBool()),
              });
            }
            var ptr45 = dataView(memory0).getUint32(ret + 48, true);
            var len45 = dataView(memory0).getUint32(ret + 52, true);
            if (ptr45 % 1 !== 0) throw new TypeError(`list pointer [${ptr45}] is not aligned to 1`);
            var result45 = new Uint8Array(memory0.buffer.slice(ptr45, ptr45 + len45 * 1));
            var ptr46 = dataView(memory0).getUint32(ret + 56, true);
            var len46 = dataView(memory0).getUint32(ret + 60, true);
            if (ptr46 % 1 !== 0) throw new TypeError(`list pointer [${ptr46}] is not aligned to 1`);
            var result46 = new Uint8Array(memory0.buffer.slice(ptr46, ptr46 + len46 * 1));
            let variant48;
            switch (dataView(memory0).getUint8(ret + 64, true)) {
              case 0: {
                variant48 = undefined;
                break;
              }
              case 1: {
                var ptr47 = dataView(memory0).getUint32(ret + 68, true);
                var len47 = dataView(memory0).getUint32(ret + 72, true);
                var result47 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr47, len47));
                variant48 = result47;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var len50 = dataView(memory0).getUint32(ret + 100, true);
            var base50 = dataView(memory0).getUint32(ret + 96, true);
            if (base50 % 4 !== 0) throw new TypeError(`list pointer [${base50}] is not aligned to 4`);
            var result50 = [];
            for (let i = 0; i < len50; i++) {
              const base = base50 + i * 8;
              var ptr49 = dataView(memory0).getUint32(base + 0, true);
              var len49 = dataView(memory0).getUint32(base + 4, true);
              var result49 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr49, len49));
              result50.push(result49);
            }
            let variant52;
            switch (dataView(memory0).getUint8(ret + 104, true)) {
              case 0: {
                variant52 = undefined;
                break;
              }
              case 1: {
                var ptr51 = dataView(memory0).getUint32(ret + 108, true);
                var len51 = dataView(memory0).getUint32(ret + 112, true);
                var result51 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr51, len51));
                variant52 = result51;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant54;
            switch (dataView(memory0).getUint8(ret + 116, true)) {
              case 0: {
                variant54 = undefined;
                break;
              }
              case 1: {
                var ptr53 = dataView(memory0).getUint32(ret + 120, true);
                var len53 = dataView(memory0).getUint32(ret + 124, true);
                var result53 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr53, len53));
                variant54 = result53;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var len57 = dataView(memory0).getUint32(ret + 132, true);
            var base57 = dataView(memory0).getUint32(ret + 128, true);
            if (base57 % 4 !== 0) throw new TypeError(`list pointer [${base57}] is not aligned to 4`);
            var result57 = [];
            for (let i = 0; i < len57; i++) {
              const base = base57 + i * 16;
              var ptr55 = dataView(memory0).getUint32(base + 0, true);
              var len55 = dataView(memory0).getUint32(base + 4, true);
              if (ptr55 % 1 !== 0) throw new TypeError(`list pointer [${ptr55}] is not aligned to 1`);
              var result55 = new Uint8Array(memory0.buffer.slice(ptr55, ptr55 + len55 * 1));
              var ptr56 = dataView(memory0).getUint32(base + 8, true);
              var len56 = dataView(memory0).getUint32(base + 12, true);
              if (ptr56 % 1 !== 0) throw new TypeError(`list pointer [${ptr56}] is not aligned to 1`);
              var result56 = new Uint8Array(memory0.buffer.slice(ptr56, ptr56 + len56 * 1));
              result57.push([result55, result56]);
            }
            var len61 = dataView(memory0).getUint32(ret + 140, true);
            var base61 = dataView(memory0).getUint32(ret + 136, true);
            if (base61 % 4 !== 0) throw new TypeError(`list pointer [${base61}] is not aligned to 4`);
            var result61 = [];
            for (let i = 0; i < len61; i++) {
              const base = base61 + i * 12;
              let enum58;
              switch (dataView(memory0).getUint8(base + 0, true)) {
                case 0: {
                  enum58 = 'prefix';
                  break;
                }
                case 1: {
                  enum58 = 'base45';
                  break;
                }
                case 2: {
                  enum58 = 'inflate';
                  break;
                }
                case 3: {
                  enum58 = 'cose';
                  break;
                }
                case 4: {
                  enum58 = 'header';
                  break;
                }
                case 5: {
                  enum58 = 'trust-lookup';
                  break;
                }
                case 6: {
                  enum58 = 'signature';
                  break;
                }
                case 7: {
                  enum58 = 'key-usage';
                  break;
                }
                case 8: {
                  enum58 = 'cwt';
                  break;
                }
                case 9: {
                  enum58 = 'time';
                  break;
                }
                case 10: {
                  enum58 = 'hcert';
                  break;
                }
                case 11: {
                  enum58 = 'payload';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Step');
                }
              }
              let enum59;
              switch (dataView(memory0).getUint8(base + 1, true)) {
                case 0: {
                  enum59 = 'blocking';
                  break;
                }
                case 1: {
                  enum59 = 'warning';
                  break;
                }
                case 2: {
                  enum59 = 'information';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Severity');
                }
              }
              var ptr60 = dataView(memory0).getUint32(base + 4, true);
              var len60 = dataView(memory0).getUint32(base + 8, true);
              var result60 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr60, len60));
              result61.push({
                step: enum58,
                severity: enum59,
                message: result60,
              });
            }
            variant69= {
              tag: 'verified',
              val: {
                iss: variant9,
                iat: dataView(memory0).getBigInt64(ret + 24, true),
                exp: dataView(memory0).getBigInt64(ret + 32, true),
                entries: result44,
                dsc: {
                  der: result45,
                  kid: result46,
                  country: variant48,
                  notBefore: dataView(memory0).getBigInt64(ret + 80, true),
                  notAfter: dataView(memory0).getBigInt64(ret + 88, true),
                  extendedKeyUsages: result50,
                  domain: variant52,
                  participant: variant54,
                },
                otherClaims: result57,
                diagnostics: result61,
              }
            };
            break;
          }
          case 1: {
            let enum62;
            switch (dataView(memory0).getUint8(ret + 8, true)) {
              case 0: {
                enum62 = 'prefix';
                break;
              }
              case 1: {
                enum62 = 'base45';
                break;
              }
              case 2: {
                enum62 = 'inflate';
                break;
              }
              case 3: {
                enum62 = 'cose';
                break;
              }
              case 4: {
                enum62 = 'header';
                break;
              }
              case 5: {
                enum62 = 'trust-lookup';
                break;
              }
              case 6: {
                enum62 = 'signature';
                break;
              }
              case 7: {
                enum62 = 'key-usage';
                break;
              }
              case 8: {
                enum62 = 'cwt';
                break;
              }
              case 9: {
                enum62 = 'time';
                break;
              }
              case 10: {
                enum62 = 'hcert';
                break;
              }
              case 11: {
                enum62 = 'payload';
                break;
              }
              default: {
                throw new TypeError('invalid discriminant specified for Step');
              }
            }
            let enum63;
            switch (dataView(memory0).getUint8(ret + 9, true)) {
              case 0: {
                enum63 = 'bad-prefix';
                break;
              }
              case 1: {
                enum63 = 'unsupported-context-version';
                break;
              }
              case 2: {
                enum63 = 'base45-invalid';
                break;
              }
              case 3: {
                enum63 = 'inflate-failed';
                break;
              }
              case 4: {
                enum63 = 'limit-exceeded';
                break;
              }
              case 5: {
                enum63 = 'cose-malformed';
                break;
              }
              case 6: {
                enum63 = 'header-invalid';
                break;
              }
              case 7: {
                enum63 = 'unsupported-algorithm';
                break;
              }
              case 8: {
                enum63 = 'kid-not-found';
                break;
              }
              case 9: {
                enum63 = 'signature-invalid';
                break;
              }
              case 10: {
                enum63 = 'key-usage-mismatch';
                break;
              }
              case 11: {
                enum63 = 'expired';
                break;
              }
              case 12: {
                enum63 = 'not-yet-valid';
                break;
              }
              case 13: {
                enum63 = 'hcert-missing';
                break;
              }
              case 14: {
                enum63 = 'multiple-subclaims';
                break;
              }
              case 15: {
                enum63 = 'payload-invalid';
                break;
              }
              default: {
                throw new TypeError('invalid discriminant specified for FailureReason');
              }
            }
            var ptr64 = dataView(memory0).getUint32(ret + 12, true);
            var len64 = dataView(memory0).getUint32(ret + 16, true);
            var result64 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr64, len64));
            var len68 = dataView(memory0).getUint32(ret + 24, true);
            var base68 = dataView(memory0).getUint32(ret + 20, true);
            if (base68 % 4 !== 0) throw new TypeError(`list pointer [${base68}] is not aligned to 4`);
            var result68 = [];
            for (let i = 0; i < len68; i++) {
              const base = base68 + i * 12;
              let enum65;
              switch (dataView(memory0).getUint8(base + 0, true)) {
                case 0: {
                  enum65 = 'prefix';
                  break;
                }
                case 1: {
                  enum65 = 'base45';
                  break;
                }
                case 2: {
                  enum65 = 'inflate';
                  break;
                }
                case 3: {
                  enum65 = 'cose';
                  break;
                }
                case 4: {
                  enum65 = 'header';
                  break;
                }
                case 5: {
                  enum65 = 'trust-lookup';
                  break;
                }
                case 6: {
                  enum65 = 'signature';
                  break;
                }
                case 7: {
                  enum65 = 'key-usage';
                  break;
                }
                case 8: {
                  enum65 = 'cwt';
                  break;
                }
                case 9: {
                  enum65 = 'time';
                  break;
                }
                case 10: {
                  enum65 = 'hcert';
                  break;
                }
                case 11: {
                  enum65 = 'payload';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Step');
                }
              }
              let enum66;
              switch (dataView(memory0).getUint8(base + 1, true)) {
                case 0: {
                  enum66 = 'blocking';
                  break;
                }
                case 1: {
                  enum66 = 'warning';
                  break;
                }
                case 2: {
                  enum66 = 'information';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Severity');
                }
              }
              var ptr67 = dataView(memory0).getUint32(base + 4, true);
              var len67 = dataView(memory0).getUint32(base + 8, true);
              var result67 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr67, len67));
              result68.push({
                step: enum65,
                severity: enum66,
                message: result67,
              });
            }
            variant69= {
              tag: 'failed',
              val: {
                step: enum62,
                reason: enum63,
                detail: result64,
                diagnostics: result68,
              }
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for VerificationResult');
          }
        }
        _debugLog('[iface="who:smart-trust/verifier@0.1.0", function="verify-cose"][Instruction::Return]', {
          funcName: 'verify-cose',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([variant69]);
        const retCopy = variant69;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn19(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let verifier010ReadUnverified;

function readUnverified(arg0, arg1, arg2) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'verifier010ReadUnverified',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var encodeRes = _utf8AllocateAndEncode(arg0, realloc0, memory0);
        var ptr0= encodeRes.ptr;
        var len0 = encodeRes.len;
        
        
        var handle2 = arg1[symbolRscHandle];
        if (!handle2 || (handleTable2[(handle2 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle1 = handleTable2[(handle2 << 1) + 1] & ~T_FLAG;
        
        var {maxInputChars: v3_0, maxInflatedBytes: v3_1, maxDepth: v3_2, maxItems: v3_3, maxStringBytes: v3_4 } = arg2;
        _debugLog('[iface="who:smart-trust/verifier@0.1.0", function="read-unverified"][Instruction::CallWasm] enter', {
          funcName: 'read-unverified',
          paramCount: 8,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => verifier010ReadUnverified(ptr0, len0, handle1, toUint32(v3_0), toUint32(v3_1), toUint32(v3_2), toUint32(v3_3), toUint32(v3_4)),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant56;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            let variant5;
            switch (dataView(memory0).getUint8(ret + 8, true)) {
              case 0: {
                variant5 = undefined;
                break;
              }
              case 1: {
                var ptr4 = dataView(memory0).getUint32(ret + 12, true);
                var len4 = dataView(memory0).getUint32(ret + 16, true);
                if (ptr4 % 1 !== 0) throw new TypeError(`list pointer [${ptr4}] is not aligned to 1`);
                var result4 = new Uint8Array(memory0.buffer.slice(ptr4, ptr4 + len4 * 1));
                variant5 = result4;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant7;
            switch (dataView(memory0).getUint8(ret + 20, true)) {
              case 0: {
                variant7 = undefined;
                break;
              }
              case 1: {
                var ptr6 = dataView(memory0).getUint32(ret + 24, true);
                var len6 = dataView(memory0).getUint32(ret + 28, true);
                var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                variant7 = result6;
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant8;
            switch (dataView(memory0).getUint8(ret + 32, true)) {
              case 0: {
                variant8 = undefined;
                break;
              }
              case 1: {
                variant8 = dataView(memory0).getBigInt64(ret + 40, true);
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            let variant9;
            switch (dataView(memory0).getUint8(ret + 48, true)) {
              case 0: {
                variant9 = undefined;
                break;
              }
              case 1: {
                variant9 = dataView(memory0).getBigInt64(ret + 56, true);
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for option');
              }
            }
            var len44 = dataView(memory0).getUint32(ret + 68, true);
            var base44 = dataView(memory0).getUint32(ret + 64, true);
            if (base44 % 8 !== 0) throw new TypeError(`list pointer [${base44}] is not aligned to 8`);
            var result44 = [];
            for (let i = 0; i < len44; i++) {
              const base = base44 + i * 160;
              let variant43;
              switch (dataView(memory0).getUint8(base + 8, true)) {
                case 0: {
                  variant43 = undefined;
                  break;
                }
                case 1: {
                  let variant42;
                  switch (dataView(memory0).getUint8(base + 16, true)) {
                    case 0: {
                      var ptr10 = dataView(memory0).getUint32(base + 24, true);
                      var len10 = dataView(memory0).getUint32(base + 28, true);
                      var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                      var ptr11 = dataView(memory0).getUint32(base + 32, true);
                      var len11 = dataView(memory0).getUint32(base + 36, true);
                      var result11 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr11, len11));
                      var ptr12 = dataView(memory0).getUint32(base + 40, true);
                      var len12 = dataView(memory0).getUint32(base + 44, true);
                      var result12 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr12, len12));
                      var ptr13 = dataView(memory0).getUint32(base + 48, true);
                      var len13 = dataView(memory0).getUint32(base + 52, true);
                      var result13 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr13, len13));
                      let variant15;
                      switch (dataView(memory0).getUint8(base + 56, true)) {
                        case 0: {
                          variant15 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr14 = dataView(memory0).getUint32(base + 60, true);
                          var len14 = dataView(memory0).getUint32(base + 64, true);
                          var result14 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr14, len14));
                          variant15 = result14;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      let variant17;
                      switch (dataView(memory0).getUint8(base + 68, true)) {
                        case 0: {
                          variant17 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr16 = dataView(memory0).getUint32(base + 72, true);
                          var len16 = dataView(memory0).getUint32(base + 76, true);
                          var result16 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr16, len16));
                          variant17 = result16;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      let variant19;
                      switch (dataView(memory0).getUint8(base + 80, true)) {
                        case 0: {
                          variant19 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr18 = dataView(memory0).getUint32(base + 84, true);
                          var len18 = dataView(memory0).getUint32(base + 88, true);
                          var result18 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr18, len18));
                          variant19 = result18;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      var ptr20 = dataView(memory0).getUint32(base + 92, true);
                      var len20 = dataView(memory0).getUint32(base + 96, true);
                      var result20 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr20, len20));
                      var ptr21 = dataView(memory0).getUint32(base + 100, true);
                      var len21 = dataView(memory0).getUint32(base + 104, true);
                      var result21 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr21, len21));
                      let variant23;
                      switch (dataView(memory0).getUint8(base + 108, true)) {
                        case 0: {
                          variant23 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr22 = dataView(memory0).getUint32(base + 112, true);
                          var len22 = dataView(memory0).getUint32(base + 116, true);
                          var result22 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr22, len22));
                          variant23 = result22;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      let variant25;
                      switch (dataView(memory0).getUint8(base + 120, true)) {
                        case 0: {
                          variant25 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr24 = dataView(memory0).getUint32(base + 124, true);
                          var len24 = dataView(memory0).getUint32(base + 128, true);
                          var result24 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr24, len24));
                          variant25 = result24;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      var ptr26 = dataView(memory0).getUint32(base + 132, true);
                      var len26 = dataView(memory0).getUint32(base + 136, true);
                      var result26 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr26, len26));
                      var ptr27 = dataView(memory0).getUint32(base + 140, true);
                      var len27 = dataView(memory0).getUint32(base + 144, true);
                      var result27 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr27, len27));
                      var len30 = dataView(memory0).getUint32(base + 152, true);
                      var base30 = dataView(memory0).getUint32(base + 148, true);
                      if (base30 % 4 !== 0) throw new TypeError(`list pointer [${base30}] is not aligned to 4`);
                      var result30 = [];
                      for (let i = 0; i < len30; i++) {
                        const base = base30 + i * 16;
                        var ptr28 = dataView(memory0).getUint32(base + 0, true);
                        var len28 = dataView(memory0).getUint32(base + 4, true);
                        var result28 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr28, len28));
                        var ptr29 = dataView(memory0).getUint32(base + 8, true);
                        var len29 = dataView(memory0).getUint32(base + 12, true);
                        if (ptr29 % 1 !== 0) throw new TypeError(`list pointer [${ptr29}] is not aligned to 1`);
                        var result29 = new Uint8Array(memory0.buffer.slice(ptr29, ptr29 + len29 * 1));
                        result30.push([result28, result29]);
                      }
                      variant42= {
                        tag: 'dvc-min',
                        val: {
                          n: result10,
                          dob: result11,
                          s: result12,
                          nt: result13,
                          id: variant15,
                          dt: variant17,
                          gn: variant19,
                          vx: {
                            vp: result20,
                            dv: result21,
                            cn: variant23,
                            is: variant25,
                            bo: result26,
                          },
                          v: result27,
                          extensions: result30,
                        }
                      };
                      break;
                    }
                    case 1: {
                      var ptr31 = dataView(memory0).getUint32(base + 24, true);
                      var len31 = dataView(memory0).getUint32(base + 28, true);
                      var result31 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr31, len31));
                      var ptr32 = dataView(memory0).getUint32(base + 32, true);
                      var len32 = dataView(memory0).getUint32(base + 36, true);
                      var result32 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr32, len32));
                      let variant33;
                      switch (dataView(memory0).getUint8(base + 40, true)) {
                        case 0: {
                          variant33 = undefined;
                          break;
                        }
                        case 1: {
                          variant33 = dataView(memory0).getBigInt64(base + 48, true);
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      let variant35;
                      switch (dataView(memory0).getUint8(base + 56, true)) {
                        case 0: {
                          variant35 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr34 = dataView(memory0).getUint32(base + 60, true);
                          var len34 = dataView(memory0).getUint32(base + 64, true);
                          var result34 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr34, len34));
                          variant35 = result34;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      let variant37;
                      switch (dataView(memory0).getUint8(base + 68, true)) {
                        case 0: {
                          variant37 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr36 = dataView(memory0).getUint32(base + 72, true);
                          var len36 = dataView(memory0).getUint32(base + 76, true);
                          var result36 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr36, len36));
                          variant37 = result36;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      let variant38;
                      switch (dataView(memory0).getUint8(base + 80, true)) {
                        case 0: {
                          variant38 = undefined;
                          break;
                        }
                        case 1: {
                          variant38 = dataView(memory0).getInt32(base + 84, true);
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      let variant40;
                      switch (dataView(memory0).getUint8(base + 88, true)) {
                        case 0: {
                          variant40 = undefined;
                          break;
                        }
                        case 1: {
                          var ptr39 = dataView(memory0).getUint32(base + 92, true);
                          var len39 = dataView(memory0).getUint32(base + 96, true);
                          var result39 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr39, len39));
                          variant40 = result39;
                          break;
                        }
                        default: {
                          throw new TypeError('invalid variant discriminant for option');
                        }
                      }
                      variant42= {
                        tag: 'vhl',
                        val: {
                          url: result31,
                          key: result32,
                          exp: variant33,
                          flag: variant35,
                          label: variant37,
                          v: variant38,
                          fhirBaseUrl: variant40,
                        }
                      };
                      break;
                    }
                    case 2: {
                      var ptr41 = dataView(memory0).getUint32(base + 28, true);
                      var len41 = dataView(memory0).getUint32(base + 32, true);
                      if (ptr41 % 1 !== 0) throw new TypeError(`list pointer [${ptr41}] is not aligned to 1`);
                      var result41 = new Uint8Array(memory0.buffer.slice(ptr41, ptr41 + len41 * 1));
                      variant42= {
                        tag: 'raw',
                        val: {
                          subclaim: dataView(memory0).getInt32(base + 24, true),
                          cbor: result41,
                        }
                      };
                      break;
                    }
                    default: {
                      throw new TypeError('invalid variant discriminant for Payload');
                    }
                  }
                  variant43 = variant42;
                  break;
                }
                default: {
                  throw new TypeError('invalid variant discriminant for option');
                }
              }
              result44.push({
                subclaim: dataView(memory0).getInt32(base + 0, true),
                payload: variant43,
              });
            }
            var len48 = dataView(memory0).getUint32(ret + 76, true);
            var base48 = dataView(memory0).getUint32(ret + 72, true);
            if (base48 % 4 !== 0) throw new TypeError(`list pointer [${base48}] is not aligned to 4`);
            var result48 = [];
            for (let i = 0; i < len48; i++) {
              const base = base48 + i * 12;
              let enum45;
              switch (dataView(memory0).getUint8(base + 0, true)) {
                case 0: {
                  enum45 = 'prefix';
                  break;
                }
                case 1: {
                  enum45 = 'base45';
                  break;
                }
                case 2: {
                  enum45 = 'inflate';
                  break;
                }
                case 3: {
                  enum45 = 'cose';
                  break;
                }
                case 4: {
                  enum45 = 'header';
                  break;
                }
                case 5: {
                  enum45 = 'trust-lookup';
                  break;
                }
                case 6: {
                  enum45 = 'signature';
                  break;
                }
                case 7: {
                  enum45 = 'key-usage';
                  break;
                }
                case 8: {
                  enum45 = 'cwt';
                  break;
                }
                case 9: {
                  enum45 = 'time';
                  break;
                }
                case 10: {
                  enum45 = 'hcert';
                  break;
                }
                case 11: {
                  enum45 = 'payload';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Step');
                }
              }
              let enum46;
              switch (dataView(memory0).getUint8(base + 1, true)) {
                case 0: {
                  enum46 = 'blocking';
                  break;
                }
                case 1: {
                  enum46 = 'warning';
                  break;
                }
                case 2: {
                  enum46 = 'information';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Severity');
                }
              }
              var ptr47 = dataView(memory0).getUint32(base + 4, true);
              var len47 = dataView(memory0).getUint32(base + 8, true);
              var result47 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr47, len47));
              result48.push({
                step: enum45,
                severity: enum46,
                message: result47,
              });
            }
            variant56= {
              tag: 'ok',
              val: {
                kid: variant5,
                iss: variant7,
                iat: variant8,
                exp: variant9,
                entries: result44,
                diagnostics: result48,
              }
            };
            break;
          }
          case 1: {
            let enum49;
            switch (dataView(memory0).getUint8(ret + 8, true)) {
              case 0: {
                enum49 = 'prefix';
                break;
              }
              case 1: {
                enum49 = 'base45';
                break;
              }
              case 2: {
                enum49 = 'inflate';
                break;
              }
              case 3: {
                enum49 = 'cose';
                break;
              }
              case 4: {
                enum49 = 'header';
                break;
              }
              case 5: {
                enum49 = 'trust-lookup';
                break;
              }
              case 6: {
                enum49 = 'signature';
                break;
              }
              case 7: {
                enum49 = 'key-usage';
                break;
              }
              case 8: {
                enum49 = 'cwt';
                break;
              }
              case 9: {
                enum49 = 'time';
                break;
              }
              case 10: {
                enum49 = 'hcert';
                break;
              }
              case 11: {
                enum49 = 'payload';
                break;
              }
              default: {
                throw new TypeError('invalid discriminant specified for Step');
              }
            }
            let enum50;
            switch (dataView(memory0).getUint8(ret + 9, true)) {
              case 0: {
                enum50 = 'bad-prefix';
                break;
              }
              case 1: {
                enum50 = 'unsupported-context-version';
                break;
              }
              case 2: {
                enum50 = 'base45-invalid';
                break;
              }
              case 3: {
                enum50 = 'inflate-failed';
                break;
              }
              case 4: {
                enum50 = 'limit-exceeded';
                break;
              }
              case 5: {
                enum50 = 'cose-malformed';
                break;
              }
              case 6: {
                enum50 = 'header-invalid';
                break;
              }
              case 7: {
                enum50 = 'unsupported-algorithm';
                break;
              }
              case 8: {
                enum50 = 'kid-not-found';
                break;
              }
              case 9: {
                enum50 = 'signature-invalid';
                break;
              }
              case 10: {
                enum50 = 'key-usage-mismatch';
                break;
              }
              case 11: {
                enum50 = 'expired';
                break;
              }
              case 12: {
                enum50 = 'not-yet-valid';
                break;
              }
              case 13: {
                enum50 = 'hcert-missing';
                break;
              }
              case 14: {
                enum50 = 'multiple-subclaims';
                break;
              }
              case 15: {
                enum50 = 'payload-invalid';
                break;
              }
              default: {
                throw new TypeError('invalid discriminant specified for FailureReason');
              }
            }
            var ptr51 = dataView(memory0).getUint32(ret + 12, true);
            var len51 = dataView(memory0).getUint32(ret + 16, true);
            var result51 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr51, len51));
            var len55 = dataView(memory0).getUint32(ret + 24, true);
            var base55 = dataView(memory0).getUint32(ret + 20, true);
            if (base55 % 4 !== 0) throw new TypeError(`list pointer [${base55}] is not aligned to 4`);
            var result55 = [];
            for (let i = 0; i < len55; i++) {
              const base = base55 + i * 12;
              let enum52;
              switch (dataView(memory0).getUint8(base + 0, true)) {
                case 0: {
                  enum52 = 'prefix';
                  break;
                }
                case 1: {
                  enum52 = 'base45';
                  break;
                }
                case 2: {
                  enum52 = 'inflate';
                  break;
                }
                case 3: {
                  enum52 = 'cose';
                  break;
                }
                case 4: {
                  enum52 = 'header';
                  break;
                }
                case 5: {
                  enum52 = 'trust-lookup';
                  break;
                }
                case 6: {
                  enum52 = 'signature';
                  break;
                }
                case 7: {
                  enum52 = 'key-usage';
                  break;
                }
                case 8: {
                  enum52 = 'cwt';
                  break;
                }
                case 9: {
                  enum52 = 'time';
                  break;
                }
                case 10: {
                  enum52 = 'hcert';
                  break;
                }
                case 11: {
                  enum52 = 'payload';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Step');
                }
              }
              let enum53;
              switch (dataView(memory0).getUint8(base + 1, true)) {
                case 0: {
                  enum53 = 'blocking';
                  break;
                }
                case 1: {
                  enum53 = 'warning';
                  break;
                }
                case 2: {
                  enum53 = 'information';
                  break;
                }
                default: {
                  throw new TypeError('invalid discriminant specified for Severity');
                }
              }
              var ptr54 = dataView(memory0).getUint32(base + 4, true);
              var len54 = dataView(memory0).getUint32(base + 8, true);
              var result54 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr54, len54));
              result55.push({
                step: enum52,
                severity: enum53,
                message: result54,
              });
            }
            variant56= {
              tag: 'err',
              val: {
                step: enum49,
                reason: enum50,
                detail: result51,
                diagnostics: result55,
              }
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/verifier@0.1.0", function="read-unverified"][Instruction::Return]', {
          funcName: 'read-unverified',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant56;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn20(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}

const handleTable0 = [T_FLAG, 0];
handleTable0._createdReps = new Set();
handleTable0._componentIdx = 0;
const finalizationRegistry0 = finalizationRegistryCreate((handle) => {
  const { rep } = rscTableRemove(handleTable0, handle);
  exports0['1'](rep);
});

HANDLE_TABLES[0] = handleTable0;
let issuer010MethodPendingSignatureToBeSigned;

class PendingSignature{
  constructor () {
    throw new Error('"PendingSignature" resource does not define a constructor');
  }
}

PendingSignature.prototype.toBeSigned = function toBeSigned() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'issuer010MethodPendingSignatureToBeSigned',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable0[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"PendingSignature\" resource.');
        }
        var handle0 = handleTable0[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="[method]pending-signature.to-be-signed"][Instruction::CallWasm] enter', {
          funcName: '[method]pending-signature.to-be-signed',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => issuer010MethodPendingSignatureToBeSigned(handle0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr2 = dataView(memory0).getUint32(ret + 0, true);
        var len2 = dataView(memory0).getUint32(ret + 4, true);
        if (ptr2 % 1 !== 0) throw new TypeError(`list pointer [${ptr2}] is not aligned to 1`);
        var result2 = new Uint8Array(memory0.buffer.slice(ptr2, ptr2 + len2 * 1));
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="[method]pending-signature.to-be-signed"][Instruction::Return]', {
          funcName: '[method]pending-signature.to-be-signed',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result2]);
        const retCopy = result2;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let issuer010MethodPendingSignatureAlgorithm;

PendingSignature.prototype.algorithm = function algorithm() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'issuer010MethodPendingSignatureAlgorithm',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (null!== null) {
    task.setReturnMemoryIdx(null);
    task.setReturnMemory((() => null)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable0[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"PendingSignature\" resource.');
        }
        var handle0 = handleTable0[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="[method]pending-signature.algorithm"][Instruction::CallWasm] enter', {
          funcName: '[method]pending-signature.algorithm',
          paramCount: 1,
          async: false,
          postReturn: false,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => issuer010MethodPendingSignatureAlgorithm(handle0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let enum2;
        switch (ret) {
          case 0: {
            enum2 = 'es256';
            break;
          }
          case 1: {
            enum2 = 'ps256';
            break;
          }
          default: {
            throw new TypeError('invalid discriminant specified for CoseAlgorithm');
          }
        }
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="[method]pending-signature.algorithm"][Instruction::Return]', {
          funcName: '[method]pending-signature.algorithm',
          paramCount: 1,
          async: false,
          postReturn: false
        });
        task.resolve([enum2]);
        task.exit();
        return enum2;
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let issuer010MethodPendingSignatureKid;

PendingSignature.prototype.kid = function kid() {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'issuer010MethodPendingSignatureKid',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'none',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle1 = this[symbolRscHandle];
        if (!handle1 || (handleTable0[(handle1 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"PendingSignature\" resource.');
        }
        var handle0 = handleTable0[(handle1 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="[method]pending-signature.kid"][Instruction::CallWasm] enter', {
          funcName: '[method]pending-signature.kid',
          paramCount: 1,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => issuer010MethodPendingSignatureKid(handle0),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        var ptr2 = dataView(memory0).getUint32(ret + 0, true);
        var len2 = dataView(memory0).getUint32(ret + 4, true);
        if (ptr2 % 1 !== 0) throw new TypeError(`list pointer [${ptr2}] is not aligned to 1`);
        var result2 = new Uint8Array(memory0.buffer.slice(ptr2, ptr2 + len2 * 1));
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="[method]pending-signature.kid"][Instruction::Return]', {
          funcName: '[method]pending-signature.kid',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        task.resolve([result2]);
        const retCopy = result2;
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn0(ret);
        cstate.mayLeave = true;
        task.exit();
        return retCopy;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
};
let issuer010Prepare;

function prepare(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'issuer010Prepare',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        var {payloads: v0_0, iss: v0_1, validity: v0_2, dscDer: v0_3, alg: v0_4, allowDevelopmentSubclaims: v0_5 } = arg0;
        var vec39 = v0_0;
        var len39 = vec39.length;
        var result39 = realloc0(0, 0, 8, len39 * 144);
        for (let i = 0; i < vec39.length; i++) {
          const e = vec39[i];
          const base = result39 + i * 144;var variant38 = e;
          switch (variant38.tag) {
            case 'dvc-min': {
              const e = variant38.val;
              dataView(memory0).setInt8(base + 0, 0, true);
              var {n: v1_0, dob: v1_1, s: v1_2, nt: v1_3, id: v1_4, dt: v1_5, gn: v1_6, vx: v1_7, v: v1_8, extensions: v1_9 } = e;
              
              var encodeRes = _utf8AllocateAndEncode(v1_0, realloc0, memory0);
              var ptr2= encodeRes.ptr;
              var len2 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 12, len2, true);
              dataView(memory0).setUint32(base + 8, ptr2, true);
              
              var encodeRes = _utf8AllocateAndEncode(v1_1, realloc0, memory0);
              var ptr3= encodeRes.ptr;
              var len3 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 20, len3, true);
              dataView(memory0).setUint32(base + 16, ptr3, true);
              
              var encodeRes = _utf8AllocateAndEncode(v1_2, realloc0, memory0);
              var ptr4= encodeRes.ptr;
              var len4 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 28, len4, true);
              dataView(memory0).setUint32(base + 24, ptr4, true);
              
              var encodeRes = _utf8AllocateAndEncode(v1_3, realloc0, memory0);
              var ptr5= encodeRes.ptr;
              var len5 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 36, len5, true);
              dataView(memory0).setUint32(base + 32, ptr5, true);
              var variant7 = v1_4;
              if (variant7 === null || variant7=== undefined) {
                dataView(memory0).setInt8(base + 40, 0, true);
              } else {
                const e = variant7;
                dataView(memory0).setInt8(base + 40, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr6= encodeRes.ptr;
                var len6 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 48, len6, true);
                dataView(memory0).setUint32(base + 44, ptr6, true);
              }
              var variant9 = v1_5;
              if (variant9 === null || variant9=== undefined) {
                dataView(memory0).setInt8(base + 52, 0, true);
              } else {
                const e = variant9;
                dataView(memory0).setInt8(base + 52, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr8= encodeRes.ptr;
                var len8 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 60, len8, true);
                dataView(memory0).setUint32(base + 56, ptr8, true);
              }
              var variant11 = v1_6;
              if (variant11 === null || variant11=== undefined) {
                dataView(memory0).setInt8(base + 64, 0, true);
              } else {
                const e = variant11;
                dataView(memory0).setInt8(base + 64, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr10= encodeRes.ptr;
                var len10 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 72, len10, true);
                dataView(memory0).setUint32(base + 68, ptr10, true);
              }
              var {vp: v12_0, dv: v12_1, cn: v12_2, is: v12_3, bo: v12_4 } = v1_7;
              
              var encodeRes = _utf8AllocateAndEncode(v12_0, realloc0, memory0);
              var ptr13= encodeRes.ptr;
              var len13 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 80, len13, true);
              dataView(memory0).setUint32(base + 76, ptr13, true);
              
              var encodeRes = _utf8AllocateAndEncode(v12_1, realloc0, memory0);
              var ptr14= encodeRes.ptr;
              var len14 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 88, len14, true);
              dataView(memory0).setUint32(base + 84, ptr14, true);
              var variant16 = v12_2;
              if (variant16 === null || variant16=== undefined) {
                dataView(memory0).setInt8(base + 92, 0, true);
              } else {
                const e = variant16;
                dataView(memory0).setInt8(base + 92, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr15= encodeRes.ptr;
                var len15 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 100, len15, true);
                dataView(memory0).setUint32(base + 96, ptr15, true);
              }
              var variant18 = v12_3;
              if (variant18 === null || variant18=== undefined) {
                dataView(memory0).setInt8(base + 104, 0, true);
              } else {
                const e = variant18;
                dataView(memory0).setInt8(base + 104, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr17= encodeRes.ptr;
                var len17 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 112, len17, true);
                dataView(memory0).setUint32(base + 108, ptr17, true);
              }
              
              var encodeRes = _utf8AllocateAndEncode(v12_4, realloc0, memory0);
              var ptr19= encodeRes.ptr;
              var len19 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 120, len19, true);
              dataView(memory0).setUint32(base + 116, ptr19, true);
              
              var encodeRes = _utf8AllocateAndEncode(v1_8, realloc0, memory0);
              var ptr20= encodeRes.ptr;
              var len20 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 128, len20, true);
              dataView(memory0).setUint32(base + 124, ptr20, true);
              var vec24 = v1_9;
              var len24 = vec24.length;
              var result24 = realloc0(0, 0, 4, len24 * 16);
              for (let i = 0; i < vec24.length; i++) {
                const e = vec24[i];
                const base = result24 + i * 16;var [tuple21_0, tuple21_1] = e;
                
                var encodeRes = _utf8AllocateAndEncode(tuple21_0, realloc0, memory0);
                var ptr22= encodeRes.ptr;
                var len22 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 4, len22, true);
                dataView(memory0).setUint32(base + 0, ptr22, true);
                var val23 = tuple21_1;
                var len23 = Array.isArray(val23) ? val23.length : val23.byteLength;
                var ptr23 = realloc0(0, 0, 1, len23 * 1);
                
                let valData23;
                const valLenBytes23 = len23 * 1;
                if (Array.isArray(val23)) {
                  // Regular array likely containing numbers, write values to memory
                  let offset = 0;
                  const dv23 = new DataView(memory0.buffer);
                  for (const v of val23) {
                    _requireValidNumericPrimitive.bind(null, 'u8')(v);
                    dv23.setUint8(ptr23+ offset, v, true);
                    offset += 1;
                  }
                } else {
                  // TypedArray / ArrayBuffer-like, direct copy
                  valData23 = new Uint8Array(val23.buffer || val23, val23.byteOffset, valLenBytes23);
                  const out23 = new Uint8Array(memory0.buffer, ptr23, valLenBytes23);
                  out23.set(valData23);
                }
                
                dataView(memory0).setUint32(base + 12, len23, true);
                dataView(memory0).setUint32(base + 8, ptr23, true);
              }
              dataView(memory0).setUint32(base + 136, len24, true);
              dataView(memory0).setUint32(base + 132, result24, true);
              break;
            }
            case 'vhl': {
              const e = variant38.val;
              dataView(memory0).setInt8(base + 0, 1, true);
              var {url: v25_0, key: v25_1, exp: v25_2, flag: v25_3, label: v25_4, v: v25_5, fhirBaseUrl: v25_6 } = e;
              
              var encodeRes = _utf8AllocateAndEncode(v25_0, realloc0, memory0);
              var ptr26= encodeRes.ptr;
              var len26 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 12, len26, true);
              dataView(memory0).setUint32(base + 8, ptr26, true);
              
              var encodeRes = _utf8AllocateAndEncode(v25_1, realloc0, memory0);
              var ptr27= encodeRes.ptr;
              var len27 = encodeRes.len;
              
              dataView(memory0).setUint32(base + 20, len27, true);
              dataView(memory0).setUint32(base + 16, ptr27, true);
              var variant28 = v25_2;
              if (variant28 === null || variant28=== undefined) {
                dataView(memory0).setInt8(base + 24, 0, true);
              } else {
                const e = variant28;
                dataView(memory0).setInt8(base + 24, 1, true);
                dataView(memory0).setBigInt64(base + 32, toInt64(e), true);
              }
              var variant30 = v25_3;
              if (variant30 === null || variant30=== undefined) {
                dataView(memory0).setInt8(base + 40, 0, true);
              } else {
                const e = variant30;
                dataView(memory0).setInt8(base + 40, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr29= encodeRes.ptr;
                var len29 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 48, len29, true);
                dataView(memory0).setUint32(base + 44, ptr29, true);
              }
              var variant32 = v25_4;
              if (variant32 === null || variant32=== undefined) {
                dataView(memory0).setInt8(base + 52, 0, true);
              } else {
                const e = variant32;
                dataView(memory0).setInt8(base + 52, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr31= encodeRes.ptr;
                var len31 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 60, len31, true);
                dataView(memory0).setUint32(base + 56, ptr31, true);
              }
              var variant33 = v25_5;
              if (variant33 === null || variant33=== undefined) {
                dataView(memory0).setInt8(base + 64, 0, true);
              } else {
                const e = variant33;
                dataView(memory0).setInt8(base + 64, 1, true);
                dataView(memory0).setInt32(base + 68, toInt32(e), true);
              }
              var variant35 = v25_6;
              if (variant35 === null || variant35=== undefined) {
                dataView(memory0).setInt8(base + 72, 0, true);
              } else {
                const e = variant35;
                dataView(memory0).setInt8(base + 72, 1, true);
                
                var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
                var ptr34= encodeRes.ptr;
                var len34 = encodeRes.len;
                
                dataView(memory0).setUint32(base + 80, len34, true);
                dataView(memory0).setUint32(base + 76, ptr34, true);
              }
              break;
            }
            case 'raw': {
              const e = variant38.val;
              dataView(memory0).setInt8(base + 0, 2, true);
              var {subclaim: v36_0, cbor: v36_1 } = e;
              dataView(memory0).setInt32(base + 8, toInt32(v36_0), true);
              var val37 = v36_1;
              var len37 = Array.isArray(val37) ? val37.length : val37.byteLength;
              var ptr37 = realloc0(0, 0, 1, len37 * 1);
              
              let valData37;
              const valLenBytes37 = len37 * 1;
              if (Array.isArray(val37)) {
                // Regular array likely containing numbers, write values to memory
                let offset = 0;
                const dv37 = new DataView(memory0.buffer);
                for (const v of val37) {
                  _requireValidNumericPrimitive.bind(null, 'u8')(v);
                  dv37.setUint8(ptr37+ offset, v, true);
                  offset += 1;
                }
              } else {
                // TypedArray / ArrayBuffer-like, direct copy
                valData37 = new Uint8Array(val37.buffer || val37, val37.byteOffset, valLenBytes37);
                const out37 = new Uint8Array(memory0.buffer, ptr37, valLenBytes37);
                out37.set(valData37);
              }
              
              dataView(memory0).setUint32(base + 16, len37, true);
              dataView(memory0).setUint32(base + 12, ptr37, true);
              break;
            }
            default: {
              throw new TypeError(`invalid variant tag value \`${JSON.stringify(variant38.tag)}\` (received \`${variant38}\`) specified for \`Payload\``);
            }
          }
        }
        var variant41 = v0_1;
        let variant41_0;
        let variant41_1;
        let variant41_2;
        if (variant41 === null || variant41=== undefined) {
          variant41_0 = 0;
          variant41_1 = 0;
          variant41_2 = 0;
        } else {
          const e = variant41;
          
          var encodeRes = _utf8AllocateAndEncode(e, realloc0, memory0);
          var ptr40= encodeRes.ptr;
          var len40 = encodeRes.len;
          
          variant41_0 = 1;
          variant41_1 = ptr40;
          variant41_2 = len40;
        }
        var {iat: v42_0, exp: v42_1 } = v0_2;
        var val43 = v0_3;
        var len43 = Array.isArray(val43) ? val43.length : val43.byteLength;
        var ptr43 = realloc0(0, 0, 1, len43 * 1);
        
        let valData43;
        const valLenBytes43 = len43 * 1;
        if (Array.isArray(val43)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv43 = new DataView(memory0.buffer);
          for (const v of val43) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv43.setUint8(ptr43+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData43 = new Uint8Array(val43.buffer || val43, val43.byteOffset, valLenBytes43);
          const out43 = new Uint8Array(memory0.buffer, ptr43, valLenBytes43);
          out43.set(valData43);
        }
        
        var val44 = v0_4;
        let enum44;
        switch (val44) {
          case 'es256': {
            enum44 = 0;
            break;
          }
          case 'ps256': {
            enum44 = 1;
            break;
          }
          default: {
            if ((v0_4) instanceof Error) {
              console.error(v0_4);
            }
            
            throw new TypeError(`"${val44}" is not one of the cases of cose-algorithm`);
          }
        }
        
        var handle46 = arg1[symbolRscHandle];
        if (!handle46 || (handleTable2[(handle46 << 1) + 1] & T_FLAG) === 0) {
          throw new TypeError('Resource error: Not a valid \"Registry\" resource.');
        }
        var handle45 = handleTable2[(handle46 << 1) + 1] & ~T_FLAG;
        
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="prepare"][Instruction::CallWasm] enter', {
          funcName: 'prepare',
          paramCount: 12,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => issuer010Prepare(result39, len39, variant41_0, variant41_1, variant41_2, toInt64(v42_0), toInt64(v42_1), ptr43, len43, enum44, v0_5 ? 1 : 0, handle45),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant57;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var handle48 = dataView(memory0).getInt32(ret + 4, true);
            var rsc47 = new.target === PendingSignature ? this : Object.create(PendingSignature.prototype);
            Object.defineProperty(rsc47, symbolRscHandle, { writable: true, value: handle48});
            finalizationRegistry0.register(rsc47, handle48, rsc47);
            Object.defineProperty(rsc47, symbolDispose, { writable: true, value: function () {
              finalizationRegistry0.unregister(rsc47);
              const handleEntry = rscTableRemove(handleTable0, handle48);
              rsc47[symbolDispose] = emptyFunc;
              rsc47[symbolRscHandle] = undefined;
              exports0['1'](handleEntry.rep);
            }});
            variant57= {
              tag: 'ok',
              val: rsc47
            };
            break;
          }
          case 1: {
            let variant56;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var len53 = dataView(memory0).getUint32(ret + 12, true);
                var base53 = dataView(memory0).getUint32(ret + 8, true);
                if (base53 % 4 !== 0) throw new TypeError(`list pointer [${base53}] is not aligned to 4`);
                var result53 = [];
                for (let i = 0; i < len53; i++) {
                  const base = base53 + i * 28;
                  var ptr49 = dataView(memory0).getUint32(base + 0, true);
                  var len49 = dataView(memory0).getUint32(base + 4, true);
                  var result49 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr49, len49));
                  var ptr50 = dataView(memory0).getUint32(base + 8, true);
                  var len50 = dataView(memory0).getUint32(base + 12, true);
                  var result50 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr50, len50));
                  var ptr51 = dataView(memory0).getUint32(base + 16, true);
                  var len51 = dataView(memory0).getUint32(base + 20, true);
                  var result51 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr51, len51));
                  let enum52;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum52 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum52 = 'warning';
                      break;
                    }
                    case 2: {
                      enum52 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result53.push({
                    path: result49,
                    code: result50,
                    message: result51,
                    severity: enum52,
                  });
                }
                variant56= {
                  tag: 'payload-invalid',
                  val: result53
                };
                break;
              }
              case 1: {
                variant56= {
                  tag: 'no-payload',
                };
                break;
              }
              case 2: {
                variant56= {
                  tag: 'duplicate-subclaim',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 3: {
                variant56= {
                  tag: 'development-subclaim-not-allowed',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 4: {
                variant56= {
                  tag: 'unknown-subclaim',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 5: {
                variant56= {
                  tag: 'validity-outside-dsc',
                };
                break;
              }
              case 6: {
                var ptr54 = dataView(memory0).getUint32(ret + 8, true);
                var len54 = dataView(memory0).getUint32(ret + 12, true);
                var result54 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr54, len54));
                variant56= {
                  tag: 'bad-certificate',
                  val: result54
                };
                break;
              }
              case 7: {
                var ptr55 = dataView(memory0).getUint32(ret + 8, true);
                var len55 = dataView(memory0).getUint32(ret + 12, true);
                var result55 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr55, len55));
                variant56= {
                  tag: 'bad-signature-format',
                  val: result55
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for IssueError');
              }
            }
            variant57= {
              tag: 'err',
              val: variant56
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="prepare"][Instruction::Return]', {
          funcName: 'prepare',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant57;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn21(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
let issuer010Finish;

function finish(arg0, arg1) {
  
  const hostProvided = false;
  getOrCreateAsyncState(0).throwIfTrapped();
  
  const [task, _wasm_call_currentTaskID] = createNewCurrentTask({
    componentIdx: 0,
    isAsync: false,
    isManualAsync: false,
    preserveFutureResult: false,
    entryFnName: 'issuer010Finish',
    getCallbackFn: () => null,
    callbackFnName: null,
    errHandling: 'throw-result-err',
    callingWasmExport: true,
  });
  task.setCalleeIsAsync(false);
  
  const started = task.enterSync();
  CURRENT_TASK_MAY_BLOCK.value = task.mayBlock() ? 1 : 0;
  
  if (0!== null) {
    task.setReturnMemoryIdx(0);
    task.setReturnMemory((() => memory0)());
  }
  
  
  return _withGlobalCurrentTaskMeta({
    taskID: task.id(),
    componentIdx: task.componentIdx(),
    fn: () => {
      try {
        
        
        var handle0 = arg0[symbolRscHandle];
        if (!handle0) {
          throw new TypeError('Resource error: Not a valid \"PendingSignature\" resource.');
        }
        finalizationRegistry0.unregister(arg0);
        arg0[symbolDispose] = emptyFunc;
        arg0[symbolRscHandle] = undefined;
        
        var val1 = arg1;
        var len1 = Array.isArray(val1) ? val1.length : val1.byteLength;
        var ptr1 = realloc0(0, 0, 1, len1 * 1);
        
        let valData1;
        const valLenBytes1 = len1 * 1;
        if (Array.isArray(val1)) {
          // Regular array likely containing numbers, write values to memory
          let offset = 0;
          const dv1 = new DataView(memory0.buffer);
          for (const v of val1) {
            _requireValidNumericPrimitive.bind(null, 'u8')(v);
            dv1.setUint8(ptr1+ offset, v, true);
            offset += 1;
          }
        } else {
          // TypedArray / ArrayBuffer-like, direct copy
          valData1 = new Uint8Array(val1.buffer || val1, val1.byteOffset, valLenBytes1);
          const out1 = new Uint8Array(memory0.buffer, ptr1, valLenBytes1);
          out1.set(valData1);
        }
        
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="finish"][Instruction::CallWasm] enter', {
          funcName: 'finish',
          paramCount: 3,
          async: false,
          postReturn: true,
        });
        
        let ret;
        
        try {
          ret =  _withGlobalCurrentTaskMeta({
            taskID: task.id(),
            componentIdx: task.componentIdx(),
            fn: () => issuer010Finish(handle0, ptr1, len1),
          });
        } catch (err) {
          
          _debugLog('[Instruction::CallWasm] error during sync call', {
            taskID: task.id(),
            err,
          });
          getOrCreateAsyncState(0).markTrapped(err);
          task.setErrored(err);
          task.reject(err);
          task.exit();
          throw err;
          
        }
        
        let variant12;
        switch (dataView(memory0).getUint8(ret + 0, true)) {
          case 0: {
            var ptr2 = dataView(memory0).getUint32(ret + 4, true);
            var len2 = dataView(memory0).getUint32(ret + 8, true);
            var result2 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr2, len2));
            var ptr3 = dataView(memory0).getUint32(ret + 12, true);
            var len3 = dataView(memory0).getUint32(ret + 16, true);
            if (ptr3 % 1 !== 0) throw new TypeError(`list pointer [${ptr3}] is not aligned to 1`);
            var result3 = new Uint8Array(memory0.buffer.slice(ptr3, ptr3 + len3 * 1));
            variant12= {
              tag: 'ok',
              val: {
                hc1: result2,
                cose: result3,
              }
            };
            break;
          }
          case 1: {
            let variant11;
            switch (dataView(memory0).getUint8(ret + 4, true)) {
              case 0: {
                var len8 = dataView(memory0).getUint32(ret + 12, true);
                var base8 = dataView(memory0).getUint32(ret + 8, true);
                if (base8 % 4 !== 0) throw new TypeError(`list pointer [${base8}] is not aligned to 4`);
                var result8 = [];
                for (let i = 0; i < len8; i++) {
                  const base = base8 + i * 28;
                  var ptr4 = dataView(memory0).getUint32(base + 0, true);
                  var len4 = dataView(memory0).getUint32(base + 4, true);
                  var result4 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr4, len4));
                  var ptr5 = dataView(memory0).getUint32(base + 8, true);
                  var len5 = dataView(memory0).getUint32(base + 12, true);
                  var result5 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr5, len5));
                  var ptr6 = dataView(memory0).getUint32(base + 16, true);
                  var len6 = dataView(memory0).getUint32(base + 20, true);
                  var result6 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr6, len6));
                  let enum7;
                  switch (dataView(memory0).getUint8(base + 24, true)) {
                    case 0: {
                      enum7 = 'blocking';
                      break;
                    }
                    case 1: {
                      enum7 = 'warning';
                      break;
                    }
                    case 2: {
                      enum7 = 'information';
                      break;
                    }
                    default: {
                      throw new TypeError('invalid discriminant specified for Severity');
                    }
                  }
                  result8.push({
                    path: result4,
                    code: result5,
                    message: result6,
                    severity: enum7,
                  });
                }
                variant11= {
                  tag: 'payload-invalid',
                  val: result8
                };
                break;
              }
              case 1: {
                variant11= {
                  tag: 'no-payload',
                };
                break;
              }
              case 2: {
                variant11= {
                  tag: 'duplicate-subclaim',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 3: {
                variant11= {
                  tag: 'development-subclaim-not-allowed',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 4: {
                variant11= {
                  tag: 'unknown-subclaim',
                  val: dataView(memory0).getInt32(ret + 8, true)
                };
                break;
              }
              case 5: {
                variant11= {
                  tag: 'validity-outside-dsc',
                };
                break;
              }
              case 6: {
                var ptr9 = dataView(memory0).getUint32(ret + 8, true);
                var len9 = dataView(memory0).getUint32(ret + 12, true);
                var result9 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr9, len9));
                variant11= {
                  tag: 'bad-certificate',
                  val: result9
                };
                break;
              }
              case 7: {
                var ptr10 = dataView(memory0).getUint32(ret + 8, true);
                var len10 = dataView(memory0).getUint32(ret + 12, true);
                var result10 = TEXT_DECODER_UTF8.decode(new Uint8Array(memory0.buffer, ptr10, len10));
                variant11= {
                  tag: 'bad-signature-format',
                  val: result10
                };
                break;
              }
              default: {
                throw new TypeError('invalid variant discriminant for IssueError');
              }
            }
            variant12= {
              tag: 'err',
              val: variant11
            };
            break;
          }
          default: {
            throw new TypeError('invalid variant discriminant for expected');
          }
        }
        _debugLog('[iface="who:smart-trust/issuer@0.1.0", function="finish"][Instruction::Return]', {
          funcName: 'finish',
          paramCount: 1,
          async: false,
          postReturn: true
        });
        const retCopy = variant12;
        task.resolve([retCopy.val]);
        
        let cstate = getOrCreateAsyncState(0);
        cstate.mayLeave = false;
        postReturn22(ret);
        cstate.mayLeave = true;
        task.exit();
        
        
        
        if (typeof retCopy === 'object' && retCopy.tag === 'err') {
          throw new ComponentError(retCopy.val);
        }
        return retCopy.val;
        
        
      } catch (err) {
        if (!task.isResolvedState()) {
          task.setErrored(err);
          task.reject(err);
        }
        if (!task.isExited()) { task.exit({ skipExclusiveLockCheck: true }); }
        throw err;
      }
    },
  });
  
}
function trampoline0(handle) {
  return rscTableGet(handleTable0, handle).rep;
}
function trampoline1(handle) {
  const handleEntry = rscTableRemove(handleTable0, handle);
  if (handleEntry.own) {
    exports0['1'](handleEntry.rep);
  }
}
const trampoline2 = rscTableCreateOwn.bind(null, handleTable0);
function trampoline3(handle) {
  const handleEntry = rscTableRemove(handleTable1, handle);
  if (handleEntry.own) {
    exports0['3'](handleEntry.rep);
  }
}
function trampoline4(handle) {
  const handleEntry = rscTableRemove(handleTable2, handle);
  if (handleEntry.own) {
    exports0['2'](handleEntry.rep);
  }
}
const trampoline5 = rscTableCreateOwn.bind(null, handleTable1);
const trampoline6 = rscTableCreateOwn.bind(null, handleTable2);
let trampoline7 = _trampoline7.manuallyAsync ? new WebAssembly.Suspending(_suspendingImport(0, _lowerImportBackwardsCompat.bind(
null,
{
  trampolineIdx: 7,
  componentIdx: 0,
  isAsync: false,
  isManualAsync: _trampoline7.manuallyAsync,
  paramLiftFns: [_liftFlatU64],
  resultLowerFns: [_lowerFlatList({
    elemLowerFn: _lowerFlatU8,
    elemSize32: 1,
    elemAlign32: 1,
  })],
  hasResultPointer: true,
  funcTypeIsAsync: false,
  getCallbackFn: () => null,
  getPostReturnFn: () => null,
  isCancellable: false,
  memoryIdx: 0,
  stringEncoding: 'utf8',
  getMemoryFn: () => memory0,
  getReallocFn: () => realloc0,
  importFn: _trampoline7,
},
))) : _lowerImportBackwardsCompat.bind(
null,
{
  trampolineIdx: 7,
  componentIdx: 0,
  isAsync: false,
  isManualAsync: _trampoline7.manuallyAsync,
  paramLiftFns: [_liftFlatU64],
  resultLowerFns: [_lowerFlatList({
    elemLowerFn: _lowerFlatU8,
    elemSize32: 1,
    elemAlign32: 1,
  })],
  hasResultPointer: true,
  funcTypeIsAsync: false,
  getCallbackFn: () => null,
  getPostReturnFn: () => null,
  isCancellable: false,
  memoryIdx: 0,
  stringEncoding: 'utf8',
  getMemoryFn: () => memory0,
  getReallocFn: () => realloc0,
  importFn: _trampoline7,
},
);

const $init = (() => {
  let gen= (function* _initGenerator () {
    const module0 = fetchCompile(new URL('./hcert.core.wasm', import.meta.url));
    const module1 = base64Compile('AGFzbQEAAAABCgJgAn5/AGABfwADBQQAAQEBBAUBcAEEBAccBQEwAAABMQABATIAAgEzAAMIJGltcG9ydHMBAAorBAsAIAAgAUEAEQAACwkAIABBAREBAAsJACAAQQIRAQALCQAgAEEDEQEACwAvCXByb2R1Y2VycwEMcHJvY2Vzc2VkLWJ5AQ13aXQtY29tcG9uZW50BzAuMjU5LjA');
    const module2 = base64Compile('AGFzbQEAAAABCgJgAn5/AGABfwACQAUGYWN0dWFsATAAAAZhY3R1YWwBMQABBmFjdHVhbAEyAAEGYWN0dWFsATMAAQRzaGltCCRpbXBvcnRzAXABBAQJCgEAQQALBAABAgMALwlwcm9kdWNlcnMBDHByb2Nlc3NlZC1ieQENd2l0LWNvbXBvbmVudAcwLjI1OS4w');
    const instanceFlags0 = new WebAssembly.Global({ value: "i32", mutable: true }, 1);
    INSTANCE_FLAGS.set(0, instanceFlags0);
    ({ exports: exports0 } = yield instantiateCore(yield module1));
    ({ exports: exports1 } = yield instantiateCore(yield module0, {
      '[export]who:smart-trust/issuer@0.1.0': {
        '[resource-drop]pending-signature': Object.assign(_guardMayLeave(0, trampoline1), { _jcoMaySuspend: false }),
        '[resource-new]pending-signature': Object.assign(_guardMayLeave(0, trampoline2), { _jcoMaySuspend: false }),
        '[resource-rep]pending-signature': Object.assign(trampoline0, { _jcoMaySuspend: false }),
      },
      '[export]who:smart-trust/subclaims@0.1.0': {
        '[resource-drop]registry': Object.assign(_guardMayLeave(0, trampoline4), { _jcoMaySuspend: false }),
        '[resource-new]registry': Object.assign(_guardMayLeave(0, trampoline6), { _jcoMaySuspend: false }),
      },
      '[export]who:smart-trust/trust@0.1.0': {
        '[resource-drop]trust-store': Object.assign(_guardMayLeave(0, trampoline3), { _jcoMaySuspend: false }),
        '[resource-new]trust-store': Object.assign(_guardMayLeave(0, trampoline5), { _jcoMaySuspend: false }),
      },
      'wasi:random/random@0.2.6': {
        'get-random-bytes': Object.assign(exports0['0'], { _jcoMaySuspend: false }),
      },
    }));
    memory0 = exports1.memory;
    realloc0 = exports1.cabi_realloc;
    
    try {
      realloc0Async = WebAssembly.promising(exports1.cabi_realloc);
    } catch(err) {
      realloc0Async = exports1.cabi_realloc;
    }
    
    ({ exports: exports2 } = yield instantiateCore(yield module2, {
      actual: {
        '0': Object.assign(trampoline7, { _jcoMaySuspend: false }),
        '1': Object.assign(exports1['who:smart-trust/issuer@0.1.0#[dtor]pending-signature'], { _jcoMaySuspend: false }),
        '2': Object.assign(exports1['who:smart-trust/subclaims@0.1.0#[dtor]registry'], { _jcoMaySuspend: false }),
        '3': Object.assign(exports1['who:smart-trust/trust@0.1.0#[dtor]trust-store'], { _jcoMaySuspend: false }),
      },
      shim: {
        $imports: exports0.$imports,
      },
    }));
    postReturn0 = exports1['cabi_post_who:smart-trust/codec@0.1.0#base45-encode'];
    
    try {
      postReturn0Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/codec@0.1.0#base45-encode']);
    } catch(err) {
      postReturn0Async = exports1['cabi_post_who:smart-trust/codec@0.1.0#base45-encode'];
    }
    
    postReturn1 = exports1['cabi_post_who:smart-trust/codec@0.1.0#base45-decode'];
    
    try {
      postReturn1Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/codec@0.1.0#base45-decode']);
    } catch(err) {
      postReturn1Async = exports1['cabi_post_who:smart-trust/codec@0.1.0#base45-decode'];
    }
    
    postReturn2 = exports1['cabi_post_who:smart-trust/codec@0.1.0#detect-context'];
    
    try {
      postReturn2Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/codec@0.1.0#detect-context']);
    } catch(err) {
      postReturn2Async = exports1['cabi_post_who:smart-trust/codec@0.1.0#detect-context'];
    }
    
    postReturn3 = exports1['cabi_post_who:smart-trust/cose@0.1.0#ecdsa-der-to-raw'];
    
    try {
      postReturn3Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/cose@0.1.0#ecdsa-der-to-raw']);
    } catch(err) {
      postReturn3Async = exports1['cabi_post_who:smart-trust/cose@0.1.0#ecdsa-der-to-raw'];
    }
    
    postReturn4 = exports1['cabi_post_who:smart-trust/cose@0.1.0#parse-sign1'];
    
    try {
      postReturn4Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/cose@0.1.0#parse-sign1']);
    } catch(err) {
      postReturn4Async = exports1['cabi_post_who:smart-trust/cose@0.1.0#parse-sign1'];
    }
    
    postReturn5 = exports1['cabi_post_who:smart-trust/cose@0.1.0#verify-sign1'];
    
    try {
      postReturn5Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/cose@0.1.0#verify-sign1']);
    } catch(err) {
      postReturn5Async = exports1['cabi_post_who:smart-trust/cose@0.1.0#verify-sign1'];
    }
    
    postReturn6 = exports1['cabi_post_who:smart-trust/subclaims@0.1.0#[method]registry.lookup'];
    
    try {
      postReturn6Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/subclaims@0.1.0#[method]registry.lookup']);
    } catch(err) {
      postReturn6Async = exports1['cabi_post_who:smart-trust/subclaims@0.1.0#[method]registry.lookup'];
    }
    
    postReturn7 = exports1['cabi_post_who:smart-trust/subclaims@0.1.0#[method]registry.all'];
    
    try {
      postReturn7Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/subclaims@0.1.0#[method]registry.all']);
    } catch(err) {
      postReturn7Async = exports1['cabi_post_who:smart-trust/subclaims@0.1.0#[method]registry.all'];
    }
    
    postReturn8 = exports1['cabi_post_who:smart-trust/dvc@0.1.0#validate'];
    
    try {
      postReturn8Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/dvc@0.1.0#validate']);
    } catch(err) {
      postReturn8Async = exports1['cabi_post_who:smart-trust/dvc@0.1.0#validate'];
    }
    
    postReturn9 = exports1['cabi_post_who:smart-trust/dvc@0.1.0#from-cbor'];
    
    try {
      postReturn9Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/dvc@0.1.0#from-cbor']);
    } catch(err) {
      postReturn9Async = exports1['cabi_post_who:smart-trust/dvc@0.1.0#from-cbor'];
    }
    
    postReturn10 = exports1['cabi_post_who:smart-trust/vhl@0.1.0#from-vhlink'];
    
    try {
      postReturn10Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/vhl@0.1.0#from-vhlink']);
    } catch(err) {
      postReturn10Async = exports1['cabi_post_who:smart-trust/vhl@0.1.0#from-vhlink'];
    }
    
    postReturn11 = exports1['cabi_post_who:smart-trust/vhl@0.1.0#build-manifest-request'];
    
    try {
      postReturn11Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/vhl@0.1.0#build-manifest-request']);
    } catch(err) {
      postReturn11Async = exports1['cabi_post_who:smart-trust/vhl@0.1.0#build-manifest-request'];
    }
    
    postReturn12 = exports1['cabi_post_who:smart-trust/vhl@0.1.0#parse-manifest'];
    
    try {
      postReturn12Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/vhl@0.1.0#parse-manifest']);
    } catch(err) {
      postReturn12Async = exports1['cabi_post_who:smart-trust/vhl@0.1.0#parse-manifest'];
    }
    
    postReturn13 = exports1['cabi_post_who:smart-trust/vhl@0.1.0#encrypt'];
    
    try {
      postReturn13Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/vhl@0.1.0#encrypt']);
    } catch(err) {
      postReturn13Async = exports1['cabi_post_who:smart-trust/vhl@0.1.0#encrypt'];
    }
    
    postReturn14 = exports1['cabi_post_who:smart-trust/vhl@0.1.0#decrypt'];
    
    try {
      postReturn14Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/vhl@0.1.0#decrypt']);
    } catch(err) {
      postReturn14Async = exports1['cabi_post_who:smart-trust/vhl@0.1.0#decrypt'];
    }
    
    postReturn15 = exports1['cabi_post_who:smart-trust/vhl@0.1.0#build-signature-base'];
    
    try {
      postReturn15Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/vhl@0.1.0#build-signature-base']);
    } catch(err) {
      postReturn15Async = exports1['cabi_post_who:smart-trust/vhl@0.1.0#build-signature-base'];
    }
    
    postReturn16 = exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.add-der'];
    
    try {
      postReturn16Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.add-der']);
    } catch(err) {
      postReturn16Async = exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.add-der'];
    }
    
    postReturn17 = exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.trust-domains'];
    
    try {
      postReturn17Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.trust-domains']);
    } catch(err) {
      postReturn17Async = exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.trust-domains'];
    }
    
    postReturn18 = exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.candidates'];
    
    try {
      postReturn18Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.candidates']);
    } catch(err) {
      postReturn18Async = exports1['cabi_post_who:smart-trust/trust@0.1.0#[method]trust-store.candidates'];
    }
    
    postReturn19 = exports1['cabi_post_who:smart-trust/verifier@0.1.0#verify'];
    
    try {
      postReturn19Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/verifier@0.1.0#verify']);
    } catch(err) {
      postReturn19Async = exports1['cabi_post_who:smart-trust/verifier@0.1.0#verify'];
    }
    
    postReturn20 = exports1['cabi_post_who:smart-trust/verifier@0.1.0#read-unverified'];
    
    try {
      postReturn20Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/verifier@0.1.0#read-unverified']);
    } catch(err) {
      postReturn20Async = exports1['cabi_post_who:smart-trust/verifier@0.1.0#read-unverified'];
    }
    
    postReturn21 = exports1['cabi_post_who:smart-trust/issuer@0.1.0#prepare'];
    
    try {
      postReturn21Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/issuer@0.1.0#prepare']);
    } catch(err) {
      postReturn21Async = exports1['cabi_post_who:smart-trust/issuer@0.1.0#prepare'];
    }
    
    postReturn22 = exports1['cabi_post_who:smart-trust/issuer@0.1.0#finish'];
    
    try {
      postReturn22Async = WebAssembly.promising(exports1['cabi_post_who:smart-trust/issuer@0.1.0#finish']);
    } catch(err) {
      postReturn22Async = exports1['cabi_post_who:smart-trust/issuer@0.1.0#finish'];
    }
    
    codec010Base45Encode = exports1['who:smart-trust/codec@0.1.0#base45-encode'];
    codec010Base45Decode = exports1['who:smart-trust/codec@0.1.0#base45-decode'];
    codec010ZlibCompress = exports1['who:smart-trust/codec@0.1.0#zlib-compress'];
    codec010ZlibInflate = exports1['who:smart-trust/codec@0.1.0#zlib-inflate'];
    codec010DetectContext = exports1['who:smart-trust/codec@0.1.0#detect-context'];
    codec010ToHc1 = exports1['who:smart-trust/codec@0.1.0#to-hc1'];
    codec010FromHc1 = exports1['who:smart-trust/codec@0.1.0#from-hc1'];
    codec010CborDeterministic = exports1['who:smart-trust/codec@0.1.0#cbor-deterministic'];
    cose010KidFromDsc = exports1['who:smart-trust/cose@0.1.0#kid-from-dsc'];
    cose010KidToBase64 = exports1['who:smart-trust/cose@0.1.0#kid-to-base64'];
    cose010KidFromBase64 = exports1['who:smart-trust/cose@0.1.0#kid-from-base64'];
    cose010ParseSign1 = exports1['who:smart-trust/cose@0.1.0#parse-sign1'];
    cose010SigStructure = exports1['who:smart-trust/cose@0.1.0#sig-structure'];
    cose010VerifySign1 = exports1['who:smart-trust/cose@0.1.0#verify-sign1'];
    cose010EcdsaDerToRaw = exports1['who:smart-trust/cose@0.1.0#ecdsa-der-to-raw'];
    cose010EcdsaRawToDer = exports1['who:smart-trust/cose@0.1.0#ecdsa-raw-to-der'];
    subclaims010ConstructorRegistry = exports1['who:smart-trust/subclaims@0.1.0#[constructor]registry'];
    subclaims010MethodRegistryLookup = exports1['who:smart-trust/subclaims@0.1.0#[method]registry.lookup'];
    subclaims010MethodRegistryRegister = exports1['who:smart-trust/subclaims@0.1.0#[method]registry.register'];
    subclaims010MethodRegistryAll = exports1['who:smart-trust/subclaims@0.1.0#[method]registry.all'];
    subclaims010MethodRegistrySubclaimFor = exports1['who:smart-trust/subclaims@0.1.0#[method]registry.subclaim-for'];
    subclaims010MethodRegistrySetSubclaimFor = exports1['who:smart-trust/subclaims@0.1.0#[method]registry.set-subclaim-for'];
    dvc010Validate = exports1['who:smart-trust/dvc@0.1.0#validate'];
    dvc010ToCbor = exports1['who:smart-trust/dvc@0.1.0#to-cbor'];
    dvc010FromCbor = exports1['who:smart-trust/dvc@0.1.0#from-cbor'];
    vhl010Validate = exports1['who:smart-trust/vhl@0.1.0#validate'];
    vhl010ToVhlink = exports1['who:smart-trust/vhl@0.1.0#to-vhlink'];
    vhl010FromVhlink = exports1['who:smart-trust/vhl@0.1.0#from-vhlink'];
    vhl010NewKey = exports1['who:smart-trust/vhl@0.1.0#new-key'];
    vhl010BuildManifestRequest = exports1['who:smart-trust/vhl@0.1.0#build-manifest-request'];
    vhl010ParseManifest = exports1['who:smart-trust/vhl@0.1.0#parse-manifest'];
    vhl010Encrypt = exports1['who:smart-trust/vhl@0.1.0#encrypt'];
    vhl010Decrypt = exports1['who:smart-trust/vhl@0.1.0#decrypt'];
    vhl010BuildSignatureBase = exports1['who:smart-trust/vhl@0.1.0#build-signature-base'];
    vhl010SignatureHeader = exports1['who:smart-trust/vhl@0.1.0#signature-header'];
    trust010ConstructorTrustStore = exports1['who:smart-trust/trust@0.1.0#[constructor]trust-store'];
    trust010MethodTrustStoreAddPem = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.add-pem'];
    trust010MethodTrustStoreAddDer = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.add-der'];
    trust010MethodTrustStoreAddDidDocument = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.add-did-document'];
    trust010MethodTrustStoreAddVerifiedDidDocument = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.add-verified-did-document'];
    trust010MethodTrustStoreLoadTrustDomains = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.load-trust-domains'];
    trust010MethodTrustStoreTrustDomains = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.trust-domains'];
    trust010MethodTrustStoreCandidates = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.candidates'];
    trust010MethodTrustStoreSize = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.size'];
    trust010MethodTrustStoreClear = exports1['who:smart-trust/trust@0.1.0#[method]trust-store.clear'];
    trust010VerifyDidProof = exports1['who:smart-trust/trust@0.1.0#verify-did-proof'];
    verifier010Verify = exports1['who:smart-trust/verifier@0.1.0#verify'];
    verifier010VerifyCose = exports1['who:smart-trust/verifier@0.1.0#verify-cose'];
    verifier010ReadUnverified = exports1['who:smart-trust/verifier@0.1.0#read-unverified'];
    issuer010MethodPendingSignatureToBeSigned = exports1['who:smart-trust/issuer@0.1.0#[method]pending-signature.to-be-signed'];
    issuer010MethodPendingSignatureAlgorithm = exports1['who:smart-trust/issuer@0.1.0#[method]pending-signature.algorithm'];
    issuer010MethodPendingSignatureKid = exports1['who:smart-trust/issuer@0.1.0#[method]pending-signature.kid'];
    issuer010Prepare = exports1['who:smart-trust/issuer@0.1.0#prepare'];
    issuer010Finish = exports1['who:smart-trust/issuer@0.1.0#finish'];
  })();
  let promise, resolve, reject;
  function normalizeInstantiationError(e) {
    // Native JSPI rejects a suspending import called from a
    // core start function before entering its JS wrapper.
    // At component instantiation time that always means the
    // implicit synchronous task attempted to block.
    if (typeof WebAssembly.SuspendError === 'function' && e instanceof WebAssembly.SuspendError) {
      return new WebAssembly.RuntimeError('cannot block a synchronous task before returning');
    }
    return e;
  }
  function runNext(value) {
    try {
      let done;
      do {
        ({ value, done } = gen.next(value));
      } while (!(value instanceof Promise) && !done);
      if (done) {
        if (resolve) resolve(value);
        else return value;
      }
      if (!promise) promise = new Promise((_resolve, _reject) => (resolve = _resolve, reject= _reject));
      value.then(runNext, e => reject(normalizeInstantiationError(e)));
    }
    catch (e) {
      e = normalizeInstantiationError(e);
      if (reject) reject(e);
      else throw e;
    }
  }
  const maybeSyncReturn = runNext(null);
  return promise || maybeSyncReturn;
})();

await $init;
const codec010 = {
  base45Decode: base45Decode,
  base45Encode: base45Encode,
  cborDeterministic: cborDeterministic,
  detectContext: detectContext,
  fromHc1: fromHc1,
  toHc1: toHc1,
  zlibCompress: zlibCompress,
  zlibInflate: zlibInflate,
  
};
const cose010 = {
  ecdsaDerToRaw: ecdsaDerToRaw,
  ecdsaRawToDer: ecdsaRawToDer,
  kidFromBase64: kidFromBase64,
  kidFromDsc: kidFromDsc,
  kidToBase64: kidToBase64,
  parseSign1: parseSign1,
  sigStructure: sigStructure,
  verifySign1: verifySign1,
  
};
const dvc010 = {
  fromCbor: fromCbor,
  toCbor: toCbor,
  validate: validate,
  
};
const issuer010 = {
  PendingSignature: PendingSignature,
  finish: finish,
  prepare: prepare,
  
};
const subclaims010 = {
  Registry: Registry,
  
};
const trust010 = {
  TrustStore: TrustStore,
  verifyDidProof: verifyDidProof,
  
};
const verifier010 = {
  readUnverified: readUnverified,
  verify: verify,
  verifyCose: verifyCose,
  
};
const vhl010 = {
  buildManifestRequest: buildManifestRequest,
  buildSignatureBase: buildSignatureBase,
  decrypt: decrypt,
  encrypt: encrypt,
  fromVhlink: fromVhlink,
  newKey: newKey,
  parseManifest: parseManifest,
  signatureHeader: signatureHeader,
  toVhlink: toVhlink,
  validate: validate$1,
  
};

export { codec010 as codec, cose010 as cose, dvc010 as dvc, issuer010 as issuer, subclaims010 as subclaims, trust010 as trust, verifier010 as verifier, vhl010 as vhl, codec010 as 'who:smart-trust/codec@0.1.0', cose010 as 'who:smart-trust/cose@0.1.0', dvc010 as 'who:smart-trust/dvc@0.1.0', issuer010 as 'who:smart-trust/issuer@0.1.0', subclaims010 as 'who:smart-trust/subclaims@0.1.0', trust010 as 'who:smart-trust/trust@0.1.0', verifier010 as 'who:smart-trust/verifier@0.1.0', vhl010 as 'who:smart-trust/vhl@0.1.0',  }
export const _util = {
  
}

