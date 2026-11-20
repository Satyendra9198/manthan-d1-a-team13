import { useEffect, useState } from 'react'
import {
  addTask,
  clearCompleted,
  removeTask,
  toggleTask,
  type Task,
} from '@/lib/tasks'

export const STORAGE_KEY = 'tasks:v1'

function load(): Task[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? (parsed as Task[]) : []
  } catch {
    return []
  }
}

export function useTasks() {
  const [tasks, setTasks] = useState<Task[]>(load)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks))
    } catch {
      // storage unavailable (private mode, quota) — keep in-memory state
    }
  }, [tasks])

  return {
    tasks,
    add: (title: string) => setTasks((ts) => addTask(ts, title)),
    toggle: (id: string) => setTasks((ts) => toggleTask(ts, id)),
    remove: (id: string) => setTasks((ts) => removeTask(ts, id)),
    clearCompleted: () => setTasks(clearCompleted),
  }
}
