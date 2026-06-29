import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { axe } from 'vitest-axe'
import * as matchers from 'vitest-axe/matchers'
import PageHeader from '@/components/PageHeader.vue'
import PageLayout from '@/components/PageLayout.vue'
import RichTextRenderer from '@/components/RichTextRenderer.vue'

expect.extend(matchers)

// Disable 'region' rule — components are rendered without landmarks in unit tests
const axeOpts = { rules: { region: { enabled: false } } }

describe('a11y — axe-core runtime checks', () => {
  it('PageHeader has no a11y violations', async () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'User Management', subtitle: 'Manage all users' },
      slots: { actions: '<button>Create User</button>' },
    })
    const results = await axe(wrapper.element, axeOpts)
    expect(results).toHaveNoViolations()
  })

  it('PageLayout (default) has no a11y violations', async () => {
    const wrapper = mount(PageLayout, {
      slots: { default: '<main><h1>Content</h1><p>Body text</p></main>' },
    })
    const results = await axe(wrapper.element, axeOpts)
    expect(results).toHaveNoViolations()
  })

  it('PageLayout (card variant) has no a11y violations', async () => {
    const wrapper = mount(PageLayout, {
      props: { variant: 'card' },
      slots: { default: '<form aria-label="Profile form"><label for="name">Name</label><input id="name" /></form>' },
    })
    const results = await axe(wrapper.element, axeOpts)
    expect(results).toHaveNoViolations()
  })

  it('PageHeader with icon has no a11y violations', async () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Department', iconColor: '#55b3ff' },
      slots: {
        icon: '<svg role="img" aria-label="dept icon"><rect /></svg>',
        actions: '<button type="button">Add</button>',
      },
    })
    const results = await axe(wrapper.element, axeOpts)
    expect(results).toHaveNoViolations()
  })

  it('RichTextRenderer output has no a11y violations', async () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<h2>Announcement</h2><p>Hello <strong>world</strong></p>' },
    })
    const results = await axe(wrapper.element, axeOpts)
    expect(results).toHaveNoViolations()
  })
})
