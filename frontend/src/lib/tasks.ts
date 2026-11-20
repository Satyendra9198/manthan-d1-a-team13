export type Task = {
  id: string
  title: string
  completed: boolean
  createdAt: number
}

export type Filter = 'all' | 'active' | 'completed'

export function createTask(title: string): Task {
  return {
    id: crypto.randomUUID(),
    title: title.trim(),
    completed: false,
    createdAt: Date.now(),
  }
}

export function addTask(tasks: Task[], title: string): Task[] {
  if (!title.trim()) return tasks
  return [createTask(title), ...tasks]
}

export function toggleTask(tasks: Task[], id: string): Task[] {
  return tasks.map((t) => (t.id === id ? { ...t, completed: !t.completed } : t))
}

export function removeTask(tasks: Task[], id: string): Task[] {
  return tasks.filter((t) => t.id !== id)
}

export function clearCompleted(tasks: Task[]): Task[] {
  return tasks.filter((t) => !t.completed)
}

export function filterTasks(tasks: Task[], filter: Filter): Task[] {
  if (filter === 'active') return tasks.filter((t) => !t.completed)
  if (filter === 'completed') return tasks.filter((t) => t.completed)
  return tasks
}

export function countTasks(tasks: Task[]) {
  const completed = tasks.filter((t) => t.completed).length
  return { total: tasks.length, completed, active: tasks.length - completed }
}
