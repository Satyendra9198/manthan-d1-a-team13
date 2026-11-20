import { describe, expect, it } from 'vitest'
import {
  addTask,
  clearCompleted,
  countTasks,
  filterTasks,
  removeTask,
  toggleTask,
  type Task,
} from './tasks'

const t = (id: string, completed = false): Task => ({
  id,
  title: `Task ${id}`,
  completed,
  createdAt: 0,
})

describe('tasks', () => {
  it('adds a trimmed task to the front', () => {
    const result = addTask([t('a')], '  Buy milk  ')
    expect(result).toHaveLength(2)
    expect(result[0]).toMatchObject({ title: 'Buy milk', completed: false })
  })

  it('ignores blank titles', () => {
    const tasks = [t('a')]
    expect(addTask(tasks, '   ')).toBe(tasks)
  })

  it('toggles only the matching task', () => {
    const result = toggleTask([t('a'), t('b')], 'b')
    expect(result.map((x) => x.completed)).toEqual([false, true])
  })

  it('removes a task by id', () => {
    expect(removeTask([t('a'), t('b')], 'a').map((x) => x.id)).toEqual(['b'])
  })

  it('clears completed tasks', () => {
    expect(clearCompleted([t('a', true), t('b')]).map((x) => x.id)).toEqual(['b'])
  })

  it('filters by status', () => {
    const tasks = [t('a', true), t('b'), t('c')]
    expect(filterTasks(tasks, 'all')).toHaveLength(3)
    expect(filterTasks(tasks, 'active').map((x) => x.id)).toEqual(['b', 'c'])
    expect(filterTasks(tasks, 'completed').map((x) => x.id)).toEqual(['a'])
  })

  it('counts tasks', () => {
    expect(countTasks([t('a', true), t('b')])).toEqual({ total: 2, completed: 1, active: 1 })
  })
})
