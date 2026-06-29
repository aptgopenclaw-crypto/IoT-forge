import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RichTextRenderer from '@/components/RichTextRenderer.vue'

describe('RichTextRenderer', () => {
  it('renders safe HTML content', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<p>Hello <strong>world</strong></p>' },
    })
    expect(wrapper.html()).toContain('<p>Hello <strong>world</strong></p>')
  })

  it('strips script tags', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<p>Hello</p><script>alert("xss")</script>' },
    })
    expect(wrapper.html()).not.toContain('<script>')
    expect(wrapper.html()).not.toContain('alert')
    expect(wrapper.html()).toContain('<p>Hello</p>')
  })

  it('strips iframe tags', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<p>Safe</p><iframe src="http://evil.com"></iframe>' },
    })
    expect(wrapper.html()).not.toContain('<iframe')
    expect(wrapper.html()).toContain('<p>Safe</p>')
  })

  it('strips object and embed tags', () => {
    const wrapper1 = mount(RichTextRenderer, {
      props: { html: '<object data="x"></object><p>Safe</p>' },
    })
    expect(wrapper1.html()).not.toContain('<object')
    expect(wrapper1.html()).toContain('<p>Safe</p>')

    const wrapper2 = mount(RichTextRenderer, {
      props: { html: '<embed src="y"><p>Safe</p>' },
    })
    expect(wrapper2.html()).not.toContain('<embed')
    expect(wrapper2.html()).toContain('<p>Safe</p>')
  })

  it('strips event handler attributes', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<img src="x" onerror="alert(1)">' },
    })
    expect(wrapper.html()).not.toContain('onerror')
    expect(wrapper.html()).not.toContain('alert')
  })

  it('strips onclick and onload attributes', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<div onclick="steal()">Click</div><img onload="xss()" src="x">' },
    })
    expect(wrapper.html()).not.toContain('onclick')
    expect(wrapper.html()).not.toContain('onload')
  })

  it('adds rel="noopener noreferrer" to target="_blank" links', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<a href="http://example.com" target="_blank">Link</a>' },
    })
    const anchor = wrapper.find('a')
    expect(anchor.attributes('rel')).toBe('noopener noreferrer')
    expect(anchor.attributes('target')).toBe('_blank')
  })

  it('renders empty string for null/undefined html', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: null as unknown as string },
    })
    expect(wrapper.find('.rich-text-content').text()).toBe('')
  })

  it('renders empty string for empty html', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '' },
    })
    expect(wrapper.find('.rich-text-content').text()).toBe('')
  })

  it('strips form and input tags', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<form action="/steal"><input type="text" name="pwd"></form>' },
    })
    expect(wrapper.html()).not.toContain('<form')
    expect(wrapper.html()).not.toContain('<input')
  })

  it('preserves safe formatting tags', () => {
    const wrapper = mount(RichTextRenderer, {
      props: { html: '<h1>Title</h1><ul><li>Item</li></ul><em>Italic</em>' },
    })
    expect(wrapper.find('h1').text()).toBe('Title')
    expect(wrapper.find('li').text()).toBe('Item')
    expect(wrapper.find('em').text()).toBe('Italic')
  })
})
