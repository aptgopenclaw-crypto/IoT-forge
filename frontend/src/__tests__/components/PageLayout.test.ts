import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from '@/components/PageHeader.vue'
import PageLayout from '@/components/PageLayout.vue'

describe('PageHeader', () => {
  it('renders title', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Users' } })
    expect(wrapper.find('.page-title').text()).toBe('Users')
  })

  it('renders subtitle when provided', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Users', subtitle: 'Manage accounts' } })
    expect(wrapper.find('.page-subtitle').text()).toBe('Manage accounts')
  })

  it('does not render subtitle element when not provided', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Users' } })
    expect(wrapper.find('.page-subtitle').exists()).toBe(false)
  })

  it('renders icon slot', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Dept', iconColor: '#55b3ff' },
      slots: { icon: '<svg data-testid="icon" />' },
    })
    const iconWrap = wrapper.find('.header-icon')
    expect(iconWrap.exists()).toBe(true)
    expect(iconWrap.attributes('style')).toContain('#55b3ff')
    expect(iconWrap.find('[data-testid="icon"]').exists()).toBe(true)
  })

  it('does not render icon wrapper when slot is empty', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test' } })
    expect(wrapper.find('.header-icon').exists()).toBe(false)
  })

  it('renders actions slot', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test' },
      slots: { actions: '<button class="action-btn">Add</button>' },
    })
    expect(wrapper.find('.header-actions').exists()).toBe(true)
    expect(wrapper.find('.action-btn').text()).toBe('Add')
  })

  it('does not render actions wrapper when slot is empty', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test' } })
    expect(wrapper.find('.header-actions').exists()).toBe(false)
  })
})

describe('PageLayout', () => {
  it('renders default variant with page-content wrapper', () => {
    const wrapper = mount(PageLayout, {
      slots: { default: '<p>Hello</p>' },
    })
    expect(wrapper.find('.page-container').exists()).toBe(true)
    expect(wrapper.find('.page-content').exists()).toBe(true)
    expect(wrapper.find('.page-card').exists()).toBe(false)
    expect(wrapper.text()).toContain('Hello')
  })

  it('renders card variant with page-card wrapper', () => {
    const wrapper = mount(PageLayout, {
      props: { variant: 'card' },
      slots: { default: '<p>Content</p>' },
    })
    expect(wrapper.find('.page-card').exists()).toBe(true)
    expect(wrapper.find('.page-content').exists()).toBe(false)
    expect(wrapper.text()).toContain('Content')
  })

  it('applies centered class when prop is true', () => {
    const wrapper = mount(PageLayout, {
      props: { centered: true, variant: 'card' },
      slots: { default: '<p>Form</p>' },
    })
    expect(wrapper.find('.page-container').classes()).toContain('centered')
  })

  it('does not apply centered class by default', () => {
    const wrapper = mount(PageLayout, {
      slots: { default: '<p>List</p>' },
    })
    expect(wrapper.find('.page-container').classes()).not.toContain('centered')
  })

  it('applies maxWidth style to page-content', () => {
    const wrapper = mount(PageLayout, {
      props: { maxWidth: '800px' },
      slots: { default: '<p>Narrow</p>' },
    })
    const content = wrapper.find('.page-content')
    expect(content.attributes('style')).toContain('max-width: 800px')
    expect(content.attributes('style')).toContain('margin: 0px auto')
  })
})
